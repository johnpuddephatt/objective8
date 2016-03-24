(ns objective8.integration.front-end.front-end
  (:require [midje.sweet :refer :all]
            [ring.mock.request :as mock]
            [peridot.core :as p]
            [objective8.integration.integration-helpers :as helpers]
            [objective8.utils :as utils]
            [objective8.config :as config]
            [objective8.core :as core]
            [objective8.front-end.templates.sign-in :as sign-in]))

(def objectives-create-request (mock/request :get "/objectives/create"))
(def objectives-post-request (mock/request :post "/objectives"))

(def default-app (core/front-end-handler helpers/test-config))

(facts "front end"
       (binding [config/enable-csrf false]
         (fact "google analytics is added to responses"
               (binding [config/environment (assoc config/environment
                                              :google-analytics-tracking-id "GOOGLE_ANALYTICS_TRACKING_ID")]
                 (let [{response :response} (p/request (helpers/front-end-context) (utils/path-for :fe/index))]
                   (:body response))) => (contains "GOOGLE_ANALYTICS_TRACKING_ID"))

         (facts "authorisation"
                (facts "unauthorised users"
                       (fact "cannot reach the objective creation page"
                             (default-app objectives-create-request) => (contains {:status 302}))
                       (fact "cannot post a new objective"
                             (default-app objectives-post-request) => (contains {:status 302}))
                       (fact "cannot post a comment"
                             (default-app (mock/request :post "/meta/comments")) => (contains {:status 302}))))))

(facts "about white-labelling"
       (fact "defaults are used when the env var is not set"
             (let [{default-response :response} (p/request (helpers/front-end-context) (utils/path-for :fe/index))]
               (:body default-response) => (every-checker (contains "/static/favicon.ico")
                                                          (contains "Objective[8]"))))
       (fact "custom app name is used when env var is set"
             (binding [config/environment (assoc config/environment
                                            :app-name "Policy Maker")]
               (let [{response :response} (p/request (helpers/front-end-context) (utils/path-for :fe/index))]
                 (:body response) => (every-checker
                                       (contains "<a href=\"/\" title=\"Go to home page\" rel=\"home\" data-l8n=\"attr/title:masthead/logo-title-attr\" class=\"masthead-logo\">Policy Maker</a>"))))))

(facts "about the alpha warnings"
       (fact "they are hidden when env var is falsey"
             (binding [config/environment (assoc config/environment :show-alpha-warnings false)]
               (let [{response :response} (p/request (helpers/front-end-context) (utils/path-for :fe/index))]
                 (:body response) =not=> (contains "\"status-bar clj-status-bar\"")
                 (:body response) =not=> (contains "\"footer-content-text footer-alpha-warning\""))))
       (fact "they are shown when env var is truthy"
             (binding [config/environment (assoc config/environment :show-alpha-warnings true)]
               (let [{response :response} (p/request (helpers/front-end-context) (utils/path-for :fe/index))]
                 (:body response) => (contains "\"status-bar clj-status-bar\"")
                 (:body response) => (contains "\"footer-content-text footer-alpha-warning\"")))))

(facts "about facebook and twitter login buttons"
       (tabular
         (fact "they are only shown when all credentials are provided"
               (binding [config/environment (-> config/environment
                                                (assoc-in [?app-credentials ?app-credentials-token] "id-token")
                                                (assoc-in [?app-credentials ?app-credentials-secret] "secret"))]
                 (let [{response :response} (p/request (helpers/front-end-context) (utils/path-for :fe/sign-in))]
                   (:body response) => (contains ?present-element)
                   (:body response) =not=> (contains ?removed-element))))

         ?app-credentials       ?app-credentials-token  ?app-credentials-secret  ?present-element        ?removed-element
         :facebook-credentials  :client-id              :client-secret           "clj-sign-in-facebook"  "clj-sign-in-twitter"
         :twitter-credentials   :consumer-token         :secret-token            "clj-sign-in-twitter"   "clj-sign-in-facebook")

       (fact "they are hidden when partial login details are provided"
             (binding [config/environment (-> config/environment
                                              (assoc-in [:facebook-credentials :client-id] "id-token")
                                              (assoc-in [:twitter-credentials :secret-token] "secret"))]
               (let [{response :response} (p/request (helpers/front-end-context) (utils/path-for :fe/sign-in))]
                 (:body response) =not=> (contains "clj-sign-in-facebook")
                 (:body response) =not=> (contains "clj-sign-in-twitter")))))

(tabular
  (fact "the helper message changes depending on which buttons are displayed"
        (let [{response :response} (p/request (helpers/front-end-context) (utils/path-for :fe/sign-in))]
          (:body response)) => (contains ?data-l8n)
        (provided
          (sign-in/twitter-credentials-present?) => ?twitter-present
          (sign-in/facebook-credentials-present?) => ?facebook-present))

        ?twitter-present  ?facebook-present  ?data-l8n
        true              true               "data-l8n=\"content:sign-in/twitter-and-facebook-helper-text\""
        true              false              "data-l8n=\"content:sign-in/twitter-helper-text\""
        false             true               "data-l8n=\"content:sign-in/facebook-helper-text\"")

(fact "the helper message is removed when the facebook and twitter buttons are hidden"
      (let [{response :response} (p/request (helpers/front-end-context) (utils/path-for :fe/sign-in))]
        (:body response)) =not=> (contains "helper-text")
      (provided
        (sign-in/twitter-credentials-present?) => false
        (sign-in/facebook-credentials-present?) => false))

(tabular
  (fact "the stonecutter login button is only shown when all credentials are provided"
        (binding [config/environment (assoc config/environment :stonecutter-auth-provider-url ?url
                                                               :stonecutter-client-id ?id
                                                               :stonecutter-client-secret ?secret)]
          (let [{response :response} (p/request (helpers/front-end-context) (utils/path-for :fe/sign-in))]
            (:body response) ?arrow (contains "clj-sign-in-d-cent"))))

  ?url   ?id   ?secret   ?arrow
  "url"  "id"  "secret"  =>
  "url"  "id"  nil       =not=>
  "url"  nil   "secret"  =not=>
  nil    "id"  "secret"  =not=>
  nil    nil   nil       =not=>)

(tabular
  (fact "the cookie message is only shown when the environment variable is set"
      (binding [config/environment (assoc config/environment :cookie-message-enabled ?enabled)]
        (let [{response :response} (p/request (helpers/front-end-context) (utils/path-for :fe/index))]
          (:body response) ?arrow (contains "clj-cookie-message"))))
  ?enabled ?arrow
  true     =>
  false    =not=>)

(fact "the more info link in the cookies message is set to the cookies page"
      (binding [config/environment (assoc config/environment :cookie-message-enabled true)]
        (let [{response :response} (p/request (helpers/front-end-context) (utils/path-for :fe/index))
              cookies-page (str utils/host-url "/cookies")]
          (:body response) =not=> (contains "${cookieLink}")
          (:body response) => (contains cookies-page)
          (:body response) =not=> (contains "${cookieLearnMore}")
          (:body response) =not=> (contains "${cookieDismiss}")
          (:body response) =not=> (contains "${cookieMessage}"))))