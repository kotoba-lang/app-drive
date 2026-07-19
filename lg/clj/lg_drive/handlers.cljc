(ns lg-drive.handlers
  "Canonical drive method handlers (ai.etzhayyim.apps.drive.*) — clj twin of
  lg_drive/handlers.py.

  Storage-agnostic (takes an `lg-drive.store/DriveStore`). These handlers are the
  SSoT for behavior (concurrency, not-found, pagination, the change feed). Binary
  content is NOT handled here — `:drive/sha256` links metadata→blob. Maps are
  wire-shaped (STRING keys); attr maps are `:drive/*` (keyword keys)."
  (:require [clojure.string :as str]
            [lg-drive.ids :as ids]
            [lg-drive.mapping :as mapping]
            [lg-drive.edn :as edn]
            [lg-drive.store :as store])
  (:import [java.util Base64]))

(defn- now-ms [] (System/currentTimeMillis))

(defn- ->int [v default]
  (cond
    (nil? v) default
    (integer? v) v
    (string? v) (parse-long v)
    :else default))

(def ^:private provider-attrs [:drive/googleFileId :drive/msDriveItemId :drive/sha256])

(defn- resolve-file
  "Return [slug attrs] for a caller-supplied file-id, or [nil nil]."
  [st file-id]
  (let [slug (ids/resolve-slug (or file-id ""))]
    (or (when slug
          (when-let [attrs (store/get-file-attrs st slug)]
            [slug attrs]))
        (when file-id
          (some (fn [attr]
                  (when-let [found (store/lookup-slug st attr file-id)]
                    (when-let [attrs (store/get-file-attrs st found)]
                      [found attrs])))
                provider-attrs))
        [nil nil])))

(defn- to-attrs
  "Wire file map (string keys) → {:drive/* value} attr map."
  [f]
  (reduce (fn [m [field attr]]
            (let [v (get f field)]
              (if (some? v) (assoc m attr v) m)))
          {} mapping/scalar-fields))

;; ── filesCreate ───────────────────────────────────────────────────────────────

(defn files-create [st inp]
  (let [slug (ids/new-slug)
        now (now-ms)
        base {"name" (get inp "name")
              "parentId" (get inp "parentId" "root")
              "isFolder" (boolean (get inp "isFolder" false))
              "trashed" false
              "starred" false
              "version" 0
              "createdAtMs" now
              "updatedAtMs" now}
        f (reduce (fn [m opt]
                    (if (some? (get inp opt)) (assoc m opt (get inp opt)) m))
                  base
                  ["mimeType" "sizeBytes" "sha256" "googleFileId" "msDriveItemId"
                   "ownerDid" "webUrl" "downloadUrl"])]
    (store/write-ops st (mapping/create-ops slug f))
    {"fileId" slug
     "file" (mapping/attrs-to-file (assoc (to-attrs f) :drive/slug slug))}))

;; ── filesGet ──────────────────────────────────────────────────────────────────

(defn files-get [st params]
  (let [[_ attrs] (resolve-file st (get params "fileId"))]
    (if-not attrs
      {"found" false}
      {"found" true "file" (mapping/attrs-to-file attrs)})))

;; ── filesList ─────────────────────────────────────────────────────────────────

(defn files-list [st params]
  (let [parent-id (get params "parentId")
        q (get params "q")
        include-trashed (= "true" (str/lower-case (str (get params "includeTrashed" "false"))))
        order-by (get params "orderBy" "name")
        offset (->int (get params "offset") 0)
        limit (->int (get params "limit") 100)
        files (mapv mapping/attrs-to-file (store/all-file-attrs st))
        keep? (fn [f]
                (and (or (nil? parent-id) (= (get f "parentId" "root") parent-id))
                     (or include-trashed (not (get f "trashed")))
                     (or (nil? q)
                         (let [nm (str/lower-case (or (get f "name") ""))
                               ql (str/lower-case q)]
                           (or (= ql nm) (str/starts-with? nm ql))))))
        filtered (filterv keep? files)
        k (get {"updatedAtMs" "updatedAtMs" "sizeBytes" "sizeBytes"} order-by "name")
        sorted (sort-by (fn [f] [(nil? (get f k)) (get f k)]) filtered)
        page (->> sorted (drop offset) (take limit) vec)]
    {"files" page "total" (count filtered) "offset" offset "limit" limit}))

;; ── filesUpdate ───────────────────────────────────────────────────────────────

(defn files-update [st inp]
  (let [[slug attrs] (resolve-file st (get inp "fileId"))]
    (if-not attrs
      {"ok" false "notFound" true}
      (if (and (contains? inp "ifVersion") (some? (get inp "ifVersion"))
               (not= (get attrs :drive/version) (get inp "ifVersion")))
        {"ok" false "conflict" true}
        (let [patch (reduce (fn [m f]
                              (if (and (contains? inp f) (some? (get inp f)))
                                (assoc m f (get inp f)) m))
                            {} ["name" "parentId" "trashed" "starred"])
              patch (assoc patch
                           "version" (inc (->int (get attrs :drive/version) 0))
                           "updatedAtMs" (now-ms))]
          (store/write-ops st (mapping/update-ops slug attrs patch))
          (let [new-attrs (or (store/get-file-attrs st slug) attrs)]
            {"ok" true "file" (mapping/attrs-to-file new-attrs)}))))))

;; ── filesDelete ───────────────────────────────────────────────────────────────

(defn files-delete [st inp]
  (let [[slug attrs] (resolve-file st (get inp "fileId"))]
    (if-not attrs
      {"ok" false "notFound" true}
      (if (and (contains? inp "ifVersion") (some? (get inp "ifVersion"))
               (not= (get attrs :drive/version) (get inp "ifVersion")))
        {"ok" false "conflict" true}
        (do (store/write-ops st [(edn/tx-retract-entity (ids/eid-for-slug slug))])
            {"ok" true})))))

;; ── about ─────────────────────────────────────────────────────────────────────

(defn about [st params]
  (let [files (store/all-file-attrs st)
        used (reduce (fn [a attrs] (+ a (->int (get attrs :drive/sizeBytes) 0))) 0 files)]
    {"about" {"ownerDid" (get params "ownerDid" "")
              "quotaTotalBytes" 0
              "quotaUsedBytes" used}}))

;; ── changes ───────────────────────────────────────────────────────────────────

(defn- encode-token [ms]
  (-> (.withoutPadding (Base64/getUrlEncoder))
      (.encodeToString (.getBytes (str "t:" ms) "UTF-8"))))

(defn- decode-token [token]
  (if (str/blank? token)
    0
    (try
      (let [s (String. (.decode (Base64/getUrlDecoder) ^String token) "UTF-8")]
        (if (str/starts-with? s "t:") (parse-long (subs s 2)) 0))
      (catch Exception _ 0))))

(defn changes [st params]
  (let [since (decode-token (get params "pageToken"))
        limit (->int (get params "limit") 100)
        files (mapv mapping/attrs-to-file (store/all-file-attrs st))
        changed (->> files
                     (filter (fn [f] (> (->int (get f "updatedAtMs") 0) since)))
                     (sort-by (fn [f] (->int (get f "updatedAtMs") 0))))
        page (vec (take limit changed))
        high (if (seq page)
               (apply max (map (fn [f] (->int (get f "updatedAtMs") 0)) page))
               since)
        out (mapv (fn [f] {"fileId" (get f "fileId") "removed" false
                           "atMs" (get f "updatedAtMs") "file" f}) page)]
    {"changes" out
     "newStartPageToken" (encode-token high)
     "hasMore" (> (count changed) limit)}))
