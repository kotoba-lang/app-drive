(ns lg-drive.ids
  "Identity helpers for drive items — clj twin of lg_drive/ids.py.

  Canonical entity id = `drive:file:{slug}` (the datomic entity ref).
  Path-based DID    = `did:web:drive.etzhayyim.com:file:{slug}`.
  AT URI            = `at://{did}/ai.etzhayyim.apps.drive.file/{slug}`."
  (:require [clojure.string :as str]))

(def ^:private slug-alphabet "0123456789abcdefghijklmnopqrstuvwxyz")
(def ^:private domain "drive.etzhayyim.com")
(def ^:private collection "ai.etzhayyim.apps.drive.file")

(defn new-slug []
  (apply str (repeatedly 16 #(rand-nth slug-alphabet))))

(defn eid-for-slug [slug] (str "drive:file:" slug))

(defn slug-from-eid [eid] (last (str/split eid #":")))

(defn did-for-slug [slug] (str "did:web:" domain ":file:" slug))

(defn uri-for-slug [slug]
  (str "at://" (did-for-slug slug) "/" collection "/" slug))

(def ^:private slug-re #"^[0-9a-z]{6,32}$")

(defn resolve-slug
  "Resolve a caller-supplied id (slug, eid, DID, or 'root') to a bare slug, or nil."
  [file-id]
  (cond
    (or (nil? file-id) (= "" file-id)) nil
    (str/starts-with? file-id "drive:file:") (slug-from-eid file-id)
    (and (str/starts-with? file-id "did:web:") (str/includes? file-id ":file:"))
    (last (str/split file-id #":file:"))
    (re-matches slug-re file-id) file-id
    :else nil))
