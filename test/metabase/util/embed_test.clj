(ns ^:mb/once metabase.util.embed-test
  (:require
   [buddy.sign.jwt :as jwt]
   [clojure.test :refer :all]
   [crypto.random :as crypto-random]
   [java-time.api :as t]
   [metabase.config :as config]
   [metabase.premium-features.token-check :as token-check]
   [metabase.premium-features.token-check-test :as token-check-test]
   [metabase.test :as mt]
   [metabase.util :as u]
   [metabase.util.embed :as embed]
   [toucan2.core :as t2]))

(def ^:private ^String token-with-alg-none
  "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJhZG1pbiI6dHJ1ZX0.3Dbtd6Z0yuSfw62fOzBGHyiL0BJp3pod_PZE-BBdR-I")

(deftest ^:parallel validate-token-test
  (testing "check that are token is in fact valid"
    (is (= {:admin true}
           (jwt/unsign token-with-alg-none "")))))

(deftest disallow-unsigned-tokens-test
  (testing "check that we disallow tokens signed with alg = none"
    (mt/with-temporary-setting-values [embedding-secret-key (crypto-random/hex 32)]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"JWT `alg` cannot be `none`"
           (embed/unsign token-with-alg-none))))))

(deftest show-static-embed-terms-test
  (mt/with-test-user :crowberto
    (mt/with-temporary-setting-values [show-static-embed-terms nil]
      (testing "Check if the user needs to accept the embedding licensing terms before static embedding"
        (when-not config/ee-available?
          (testing "should return true when user is OSS and has not accepted licensing terms"
            (is (embed/show-static-embed-terms)))
          (testing "should return false when user is OSS and has already accepted licensing terms"
            (embed/show-static-embed-terms! false)
            (is (not (embed/show-static-embed-terms)))))
        (when config/ee-available?
          (testing "should return false when an EE user has a valid token"
            (with-redefs [token-check/fetch-token-status (fn [_x]
                                                           {:valid    true
                                                            :status   "fake"
                                                            :features ["test" "fixture"]
                                                            :trial    false})]
              (mt/with-temporary-setting-values [premium-embedding-token (token-check-test/random-token)]
                (is (not (embed/show-static-embed-terms)))
                (embed/show-static-embed-terms! false)
                (is (not (embed/show-static-embed-terms))))))
          (testing "when an EE user doesn't have a valid token"
            (mt/with-temporary-setting-values [premium-embedding-token nil show-static-embed-terms nil]
              (testing "should return true when the user has not accepted licensing terms"
                (is (embed/show-static-embed-terms)))
              (testing "should return false when the user has already accepted licensing terms"
                (embed/show-static-embed-terms! false)
                (is (not (embed/show-static-embed-terms)))))))))))

(deftest maybe-populate-initially-published-at-test
  (let [now #t "2022-09-01T12:34:56Z"]
    (doseq [model [:model/Card :model/Dashboard]]
      (testing "should populate `initially_published_at` when a Card's enable_embedding is changed to true"
        (mt/with-temp [model card {:enable_embedding false}]
          (is (nil? (:initially_published_at card)))
          (t2/update! model (u/the-id card) {:enable_embedding true})
          (is (some? (t2/select-one-fn :initially_published_at model :id (u/the-id card))))))
      (testing "should keep `initially_published_at` value when a Card's enable_embedding is changed to false"
        (mt/with-temp [model card {:enable_embedding true :initially_published_at now}]
          (is (some? (:initially_published_at card)))
          (t2/update! model (u/the-id card) {:enable_embedding false})
          (is (= (t/offset-date-time now) (t2/select-one-fn :initially_published_at model :id (u/the-id card))))))
      (testing "should keep `initially_published_at` value when `enable_embedding` is already set to true"
        (mt/with-temp [model card {:enable_embedding true :initially_published_at now}]
          (t2/update! model (u/the-id card) {:enable_embedding true})
          (is (= (t/offset-date-time now) (t2/select-one-fn :initially_published_at model :id (u/the-id card))))))
      (testing "should keep `initially_published_at` value when `enable_embedding` is already set to false"
        (mt/with-temp [model card {:enable_embedding false}]
          (is (nil? (:initially_published_at card)))
          (t2/update! model (u/the-id card) {:enable_embedding false})
          (is (nil? (t2/select-one-fn :initially_published_at model :id (u/the-id card)))))))))
