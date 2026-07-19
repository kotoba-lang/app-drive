(ns lg-drive.store
  "Drive persistence stores — clj twin of lg_drive/store.py.

  `DriveStore` is the narrow protocol the handlers use. Two implementations:
  - `KotobaDriveStore` — production, backed by kotoba datomic (graph `drive-v1`).
  - `FakeDriveStore`   — in-memory atom, used by the deterministic tests.

  Both speak the same EDN tx-op vocabulary ([:db/add ..] / [:db/retract ..] /
  [:db.fn/retractEntity ..]), so the handlers are storage-agnostic. The Python
  store was async (httpx); babashka.http-client is synchronous, so this twin is
  synchronous — the handler topology is unchanged."
  (:require [clojure.string :as str]
            [lg-drive.edn :as edn]
            [lg-drive.ids :as ids]
            [lg-drive.kotoba-datomic :as kd]))

(defprotocol DriveStore
  (get-file-attrs [store slug] "Pull the :drive/* attr map for a slug, or nil.")
  (all-file-attrs [store] "Every File entity's :drive/* attr map.")
  (lookup-slug [store attr value] "Slug of the entity with attr=value, or nil.")
  (write-ops [store ops] "Apply a vector of EDN tx-ops."))

;; ── kotoba datomic implementation ─────────────────────────────────────────────

(defrecord KotobaDriveStore [dm]
  DriveStore
  (get-file-attrs [_ slug]
    (kd/pull dm (ids/eid-for-slug slug)))
  (all-file-attrs [this]
    (let [rows (kd/q dm "[:find ?slug :where [?e :drive/type \"File\"] [?e :drive/slug ?slug]]")]
      (into [] (keep (fn [row]
                       (when-let [s (first row)]
                         (get-file-attrs this (str s))))
                     rows))))
  (lookup-slug [_ attr value]
    ;; Inline the EDN-encoded value into the query (proven yatabase pattern),
    ;; rather than an `:in $ ?v` binding.
    (let [bare (let [s (if (keyword? attr) (subs (str attr) 1) (str attr))]
                 (if (str/starts-with? s ":") (subs s 1) s))
          query (str "[:find ?slug :where [?e :" bare " " (edn/encode value)
                     "] [?e :drive/slug ?slug]]")
          rows (kd/q dm query)]
      (when-let [s (first (first rows))] (str s))))
  (write-ops [_ ops]
    (when (seq ops) (kd/transact dm ops))))

(defn kotoba-store
  ([] (->KotobaDriveStore (kd/make-client)))
  ([dm] (->KotobaDriveStore dm)))

;; ── in-memory fake (tests) ────────────────────────────────────────────────────
;; atom of {slug -> {:drive/attr value}}.

(defrecord FakeDriveStore [db]
  DriveStore
  (get-file-attrs [_ slug]
    (get @db slug))
  (all-file-attrs [_]
    (vec (vals @db)))
  (lookup-slug [_ attr value]
    (some (fn [[slug attrs]] (when (= (get attrs attr) value) slug)) @db))
  (write-ops [_ ops]
    (doseq [op ops]
      (let [kind (first op)]
        (if (= kind :db.fn/retractEntity)
          (swap! db dissoc (ids/slug-from-eid (str (nth op 1))))
          (let [[_ eid attr value] op
                slug (ids/slug-from-eid (str eid))]
            (case kind
              :db/add (swap! db assoc-in [slug attr] value)
              :db/retract (swap! db update slug
                                 (fn [row] (if (= (get row attr) value)
                                             (dissoc row attr) row))))))))))

(defn fake-store [] (->FakeDriveStore (atom {})))
