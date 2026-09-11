(ns lg-drive.test-handlers
  "Deterministic drive-handler tests using the in-memory FakeDriveStore — clj twin
  of tests/test_handlers.py (ADR-2606280030).

  Verifies create→read round-trip, list filtering (parent / q / trashed) +
  offset/limit/total pagination, version-based optimistic concurrency, the change
  feed cursor, provider-id lookup, and the about quota — without a live kotoba pod."
  (:require [clojure.test :refer [deftest is]]
            [lg-drive.handlers :as h]
            [lg-drive.store :as store]))

(defn- fresh [] (store/fake-store))

(deftest test-create-get-roundtrip
  (let [st (fresh)
        res (h/files-create st {"name" "report.pdf" "mimeType" "application/pdf"
                                "sizeBytes" 1234 "sha256" "abc"})]
    (is (= "report.pdf" (get-in res ["file" "name"])))
    (is (= 0 (get-in res ["file" "version"])))
    (let [got (h/files-get st {"fileId" (get res "fileId")})]
      (is (true? (get got "found")))
      (is (= 1234 (get-in got ["file" "sizeBytes"]))))))

(deftest test-get-missing
  (is (= {"found" false} (h/files-get (fresh) {"fileId" "missing000001"}))))

(deftest test-lookup-by-provider-id-and-sha
  (let [st (fresh)]
    (h/files-create st {"name" "x" "googleFileId" "gdrive_1" "sha256" "deadbeef"})
    (is (true? (get (h/files-get st {"fileId" "gdrive_1"}) "found")))
    (is (true? (get (h/files-get st {"fileId" "deadbeef"}) "found")))))

(deftest test-list-parent-trash-pagination
  (let [st (fresh)]
    (h/files-create st {"name" "a" "parentId" "root"})
    (h/files-create st {"name" "b" "parentId" "root"})
    (h/files-create st {"name" "c" "parentId" "folder1"})
    (let [trashed (h/files-create st {"name" "d" "parentId" "root"})]
      (h/files-update st {"fileId" (get trashed "fileId") "trashed" true}))
    (let [root (h/files-list st {"parentId" "root"})]
      (is (= 2 (get root "total")))                     ; a, b (trashed d excluded)
      (is (= #{"a" "b"} (set (map #(get % "name") (get root "files"))))))
    (let [withtrash (h/files-list st {"parentId" "root" "includeTrashed" "true"})]
      (is (= 3 (get withtrash "total"))))
    (let [page (h/files-list st {"offset" 0 "limit" 2})]
      (is (= 0 (get page "offset")))
      (is (= 2 (get page "limit")))
      (is (= 2 (count (get page "files")))))
    (let [byname (h/files-list st {"q" "c"})]
      (is (= #{"c"} (set (map #(get % "name") (get byname "files"))))))))

(deftest test-update-version-concurrency
  (let [st (fresh)
        fid (get (h/files-create st {"name" "v0"}) "fileId")
        ok (h/files-update st {"fileId" fid "ifVersion" 0 "name" "v1"})]
    (is (true? (get ok "ok")))
    (is (= "v1" (get-in ok ["file" "name"])))
    (is (= 1 (get-in ok ["file" "version"])))
    (is (= {"ok" false "conflict" true}
           (h/files-update st {"fileId" fid "ifVersion" 0 "name" "v2"})))
    (is (= {"ok" false "notFound" true}
           (h/files-update st {"fileId" "nope01" "name" "x"})))))

(deftest test-delete-concurrency
  (let [st (fresh)
        fid (get (h/files-create st {"name" "del"}) "fileId")]
    (is (= {"ok" false "conflict" true}
           (h/files-delete st {"fileId" fid "ifVersion" 9})))
    (is (= {"ok" true} (h/files-delete st {"fileId" fid "ifVersion" 0})))
    (is (= {"found" false} (h/files-get st {"fileId" fid})))))

(deftest test-changes-feed-cursor
  (let [st (fresh)]
    (h/files-create st {"name" "f1"})
    (let [first-res (h/changes st {})]
      (is (= 1 (count (get first-res "changes"))))
      (let [token (get first-res "newStartPageToken")
            second-res (h/changes st {"pageToken" token})]
        (is (= [] (get second-res "changes")))))))

(deftest test-about-quota
  (let [st (fresh)]
    (h/files-create st {"name" "big" "sizeBytes" 100})
    (h/files-create st {"name" "small" "sizeBytes" 23})
    (is (= 123 (get-in (h/about st {}) ["about" "quotaUsedBytes"])))))
