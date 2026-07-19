(ns lg-drive.edn
  "Minimal Clojure ↔ EDN helpers for kotoba datomic — clj twin of lg_drive/edn.py
  (ADR-2606280030 langgraph-python → langgraph-clj port).

  In Clojure, tx-ops are NATIVE EDN data: keywords (`:db/add`, `:drive/name`) and
  vectors need no custom encoder — `pr-str` already emits the exact wire form
  kotoba-server's `kotoba_edn::parse` understands. This ns therefore keeps the
  Python module's public surface (`tx-add` / `tx-retract` / `tx-retract-entity` /
  `encode` / `encode-tx-data` / `parse-edn-value`) as thin, faithful wrappers."
  (:require [clojure.string :as str]))

;; ── encode (Python `encode`) ─────────────────────────────────────────────────
;; `pr-str` is a total EDN encoder for the value subset calendar/drive use
;; (nil/bool/int/double/string/vector/set/map with keyword keys). It matches the
;; Python encoder byte-for-byte on those (strings quoted+escaped, true/false/nil,
;; bare ints). Used to inline a value into a `:find` query (lookup-slug).
(defn encode ^String [value] (pr-str value))

;; ── tx ops (Python `tx_add` / `tx_retract` / `tx_retract_entity`) ────────────
(defn- ->attr-kw
  "'drive/name' or ':drive/name' or :drive/name → :drive/name keyword."
  [a]
  (cond
    (keyword? a) a
    (str/starts-with? (str a) ":") (keyword (subs (str a) 1))
    :else (keyword a)))

(defn tx-add
  "[:db/add <e> <a> <v>] tx op (a may be a bare string or keyword)."
  [e a v] [:db/add e (->attr-kw a) v])

(defn tx-retract
  "[:db/retract <e> <a> <v>] tx op."
  [e a v] [:db/retract e (->attr-kw a) v])

(defn tx-retract-entity
  "[:db.fn/retractEntity <e>] — atomic full-entity delete (hard delete)."
  [e] [:db.fn/retractEntity e])

(defn encode-tx-data
  "Encode a sequence of tx-ops as a single EDN vector string."
  ^String [ops] (pr-str (vec ops)))

;; ── scalar decode (Python `parse_edn_value`) ─────────────────────────────────
(def ^:private int-re #"^-?\d+$")
(def ^:private float-re #"^-?\d+\.\d+([eE][+-]?\d+)?$")

(defn parse-edn-value
  "Decode a single EDN scalar string to a Clojure value (tolerant); mirrors the
  Python decoder exactly, including the trailing passthrough for bare words."
  [s]
  (if-not (string? s)
    s
    (cond
      (and (>= (count s) 2) (str/starts-with? s "\"") (str/ends-with? s "\""))
      (-> (subs s 1 (dec (count s)))
          (str/replace "\\\"" "\"")
          (str/replace "\\\\" "\\")
          (str/replace "\\n" "\n"))
      (= s "true") true
      (= s "false") false
      (= s "nil") nil
      (re-matches int-re s) (parse-long s)
      (re-matches float-re s) (parse-double s)
      :else s)))
