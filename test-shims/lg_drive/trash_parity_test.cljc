(ns lg-drive.trash-parity-test
  "aiueos/drive_trash.cljk（判定コア）と lg-drive.handlers の parity。
  FakeDriveStore 上の実関数（files-create / files-list / files-update）が
  guest の判定（trashed の可視性 / ifVersion 競合 / patch 白名单）と一致する。"
  (:require [clojure.test :refer [deftest is testing]]
            [lg-drive.handlers :as h]
            [lg-drive.store :as store]))

(deftest trash-visibility-matches-guest
  (let [st (store/fake-store)
        a (get (h/files-create st {"name" "a.txt" "mimeType" "text/plain"}) "fileId")
        b (get (h/files-create st {"name" "b.txt" "mimeType" "text/plain"}) "fileId")]
    ;; b をゴミ箱へ（patch 白名单の trashed — guest test-patch）
    (is (true? (get (h/files-update st {"fileId" b "trashed" true}) "ok")))
    (testing "guest test-list: trashed は一覧に出ない"
      (is (= 1 (count (get (h/files-list st {}) "files")))))
    (testing "guest test-list-trashed: includeTrashed=true のときだけ可視"
      (is (= 2 (count (get (h/files-list st {"includeTrashed" "true"}) "files")))))))

(deftest update-matches-guest
  (let [st (store/fake-store)
        id (get (h/files-create st {"name" "a.txt" "mimeType" "text/plain"}) "fileId")]
    (testing "guest test-update: ifVersion 一致は進行"
      (is (true? (get (h/files-update st {"fileId" id "ifVersion" 0 "name" "b.txt"}) "ok"))))
    (testing "guest test-update-conflict: ifVersion 不一致は conflict（412）"
      (is (true? (get (h/files-update st {"fileId" id "ifVersion" 99 "name" "x"}) "conflict"))))
    (testing "guest test-update-missing: 無い id は notFound"
      (is (true? (get (h/files-update st {"fileId" "nosuch00" "name" "x"}) "notFound"))))))
