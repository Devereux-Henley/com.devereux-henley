(ns com.devereux-henley.rts-api.method-override-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rts-api.method-override :as method-override]))

(defn- echo-method-handler
  [request]
  {:status 200 :body (:request-method request)})

(def ^:private wrapped
  (method-override/wrap-method-override echo-method-handler))

(deftest unwraps-overridable-verbs-on-post
  (testing "POST + ?_method=DELETE → :delete"
    (is (= :delete (:body (wrapped {:request-method :post :query-string "_method=DELETE"})))))
  (testing "POST + ?_method=PATCH → :patch"
    (is (= :patch (:body (wrapped {:request-method :post :query-string "_method=PATCH"})))))
  (testing "POST + ?_method=PUT → :put"
    (is (= :put (:body (wrapped {:request-method :post :query-string "_method=PUT"}))))))

(deftest case-insensitive-override-values
  (testing "lower-case override value works"
    (is (= :delete (:body (wrapped {:request-method :post :query-string "_method=delete"})))))
  (testing "mixed-case override value works"
    (is (= :patch (:body (wrapped {:request-method :post :query-string "_method=PaTcH"}))))))

(deftest leaves-other-requests-untouched
  (testing "POST without _method query param stays :post"
    (is (= :post (:body (wrapped {:request-method :post :query-string nil}))))
    (is (= :post (:body (wrapped {:request-method :post :query-string ""}))))
    (is (= :post (:body (wrapped {:request-method :post :query-string "version=1"})))))
  (testing "GET + _method=DELETE stays :get (override only fires on POST)"
    (is (= :get (:body (wrapped {:request-method :get :query-string "_method=DELETE"})))))
  (testing "PUT + _method=DELETE stays :put"
    (is (= :put (:body (wrapped {:request-method :put :query-string "_method=DELETE"}))))))

(deftest unknown-override-value-ignored
  (testing "POST + ?_method=GET stays :post — GET is not overridable"
    (is (= :post (:body (wrapped {:request-method :post :query-string "_method=GET"})))))
  (testing "POST + ?_method=POST stays :post — POST is not overridable"
    (is (= :post (:body (wrapped {:request-method :post :query-string "_method=POST"})))))
  (testing "POST + ?_method=FROBNICATE stays :post"
    (is (= :post (:body (wrapped {:request-method :post :query-string "_method=FROBNICATE"}))))))

(deftest mixed-with-other-query-params
  (testing "_method works alongside other params (leading)"
    (is (= :patch (:body (wrapped {:request-method :post :query-string "_method=PATCH&version=1"})))))
  (testing "_method works alongside other params (trailing)"
    (is (= :delete (:body (wrapped {:request-method :post :query-string "section=main&_method=DELETE"})))))
  (testing "url-encoded _method value"
    (is (= :delete (:body (wrapped {:request-method :post :query-string "_method=%44ELETE"}))))))
