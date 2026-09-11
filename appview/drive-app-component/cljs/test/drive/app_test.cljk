(ns drive.app-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :as rf-db]
            [drive.app :as app]))

(use-fixtures :each
  {:before (fn [] (rf/clear-subscription-cache!) (reset! rf-db/app-db {}))})

(deftest initialize-db-sets-defaults
  (testing ":initialize-db populates every fact App.svelte held"
    (rf/dispatch-sync [:initialize-db])
    (is (= app/default-db @rf-db/app-db))
    (is (= "drive.etzhayyim.com" @(rf/subscribe [:app/eyebrow])))
    (is (= "Workspace Drive" @(rf/subscribe [:app/title])))
    (is (= "Connect" @(rf/subscribe [:app/connect-label])))
    (is (= 3 (count @(rf/subscribe [:app/status]))))
    (is (= ["Sync" "Recent" "Security"]
           (map :status/label @(rf/subscribe [:app/status]))))
    (is (= ["Ready" "0 files" "E2EE path"]
           (map :status/value @(rf/subscribe [:app/status]))))))

(deftest status-sub-reflects-db
  (testing ":app/status reads whatever is in the db, not a fixed value"
    (reset! rf-db/app-db {:app/status [{:status/label "Only" :status/value "One" :status/description "d"}]})
    (is (= [{:status/label "Only" :status/value "One" :status/description "d"}]
           @(rf/subscribe [:app/status])))))

(deftest eyebrow-sub-reflects-db
  (testing ":app/eyebrow reads whatever is in the db, not a fixed value"
    (reset! rf-db/app-db {:app/eyebrow "other.example.com"})
    (is (= "other.example.com" @(rf/subscribe [:app/eyebrow])))))

(deftest initialize-db-overwrites-prior-state
  (testing ":initialize-db resets to defaults even if the db already had other data"
    (reset! rf-db/app-db {:app/title "stale" :app/eyebrow "stale" :unrelated 42})
    (rf/dispatch-sync [:initialize-db])
    (is (= app/default-db @rf-db/app-db))))
