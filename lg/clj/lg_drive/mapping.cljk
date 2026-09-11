(ns lg-drive.mapping
  "Canonical file ↔ :drive/* datom mapping (ADR-2606010500 D4) — clj twin of
  lg_drive/mapping.py.

  A canonical file is the `ai.etzhayyim.apps.drive.defs#file` shape (wire JSON,
  STRING keys). In datomic it is one entity `drive:file:{slug}` with `:drive/*`
  attributes (KEYWORD keys). Join key `:drive/sha256`."
  (:require [lg-drive.ids :as ids]
            [lg-drive.edn :as edn]))

;; Ordered wire-field → :drive/* attr (vector of pairs preserves create-op order).
(def scalar-fields
  [["googleFileId" :drive/googleFileId]
   ["msDriveItemId" :drive/msDriveItemId]
   ["name" :drive/name]
   ["mimeType" :drive/mimeType]
   ["isFolder" :drive/isFolder]
   ["parentId" :drive/parentId]
   ["sizeBytes" :drive/sizeBytes]
   ["sha256" :drive/sha256]
   ["webUrl" :drive/webUrl]
   ["downloadUrl" :drive/downloadUrl]
   ["createdAtMs" :drive/createdAtMs]
   ["updatedAtMs" :drive/updatedAtMs]
   ["trashed" :drive/trashed]
   ["ownerDid" :drive/ownerDid]
   ["starred" :drive/starred]
   ["version" :drive/version]])

(def defaults
  {"isFolder" false "trashed" false "starred" false "version" 0 "parentId" "root"})

(defn create-ops
  "Tx-ops to create entity `drive:file:{slug}` from a wire `file` map."
  [slug file]
  (let [eid (ids/eid-for-slug slug)
        base [(edn/tx-add eid :drive/type "File")
              (edn/tx-add eid :drive/id eid)
              (edn/tx-add eid :drive/slug slug)]]
    (into base
          (keep (fn [[field attr]]
                  (let [v (get file field (get defaults field))]
                    (when (some? v) (edn/tx-add eid attr v))))
                scalar-fields))))

(defn update-ops
  "Retract/add tx-ops for the changed fields of `patch` (wire keys) vs current
  attrs (`:drive/*` keys)."
  [slug current-attrs patch]
  (let [eid (ids/eid-for-slug slug)]
    (reduce
     (fn [ops [field attr]]
       (if-not (contains? patch field)
         ops
         (let [new-v (get patch field)
               old-v (get current-attrs attr)]
           (if (= old-v new-v)
             ops
             (cond-> ops
               (some? old-v) (conj (edn/tx-retract eid attr old-v))
               (some? new-v) (conj (edn/tx-add eid attr new-v)))))))
     []
     scalar-fields)))

(defn attrs-to-file
  "Reshape a `:drive/*` attr map back into the wire `file` JSON shape."
  [attrs]
  (let [slug (or (get attrs :drive/slug)
                 (ids/slug-from-eid (get attrs :drive/id "drive:file:unknown")))
        f (reduce (fn [m [field attr]]
                    (let [v (get attrs attr)]
                      (if (some? v) (assoc m field v) m)))
                  {"fileId" slug}
                  scalar-fields)]
    (-> f
        (update "isFolder" #(if (nil? %) (get defaults "isFolder") %))
        (update "trashed" #(if (nil? %) (get defaults "trashed") %))
        (update "version" #(if (nil? %) (get defaults "version") %)))))
