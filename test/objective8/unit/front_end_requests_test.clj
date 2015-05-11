(ns objective8.unit.front-end-requests-test
  (:require [midje.sweet :refer :all]
            [objective8.front-end-requests :refer :all]
            [objective8.utils :as utils]))

(def USER_ID 1)
(def OBJECTIVE_ID 2)
(def QUESTION_ID 3)

(def test-objective {:title "My Objective"
                     :description "I like cake"})

(def invalid-test-objective {:title ""
                             :description "I like cake"})

(def date (utils/string->date-time "2015-01-01"))
(def date-plus-30-days (utils/string->date-time "2015-01-31"))

(defn string-of-length [l]
  (apply str (repeat l "x")))

(facts "about transforming requests to objective-data"
       (fact "creates an objective from a request"
             (let [objective-data (request->objective-data {:params test-objective} USER_ID date)]
               (:data objective-data) => (assoc test-objective :created-by-id USER_ID :end-date date-plus-30-days)
               (:status objective-data) => ::objective8.front-end-requests/valid
               (:report objective-data) => {}))

       (fact "reports validation errors from a request"
             (let [objective-data (request->objective-data {:params invalid-test-objective} USER_ID date)]
               (:data objective-data) => (assoc invalid-test-objective :created-by-id USER_ID :end-date date-plus-30-days)
               (:status objective-data) => ::objective8.front-end-requests/invalid
               (:report objective-data) => {:title #{:length}})))

(facts "about transforming requests to question-data"
       (fact "extracts the relevant data"
             (let [question-data (request->question-data {:route-params {:id (str OBJECTIVE_ID)}
                                                          :params {:question "The question"}}
                                                         USER_ID)]
               (:data question-data) => {:question "The question"
                                         :created-by-id USER_ID
                                         :objective-id OBJECTIVE_ID}
               (:status question-data) => ::objective8.front-end-requests/valid
               (:report question-data) => {}))
       
       (fact "reports validation errors"
             (let [question-data (request->question-data {:route-params {:id (str OBJECTIVE_ID)}
                                                          :params {:question "Why?"}}
                                                         USER_ID)]
               (:data question-data) => {:question "Why?"
                                         :created-by-id USER_ID
                                         :objective-id OBJECTIVE_ID}
               (:status question-data) => ::objective8.front-end-requests/invalid
               (:report question-data) => {:question #{:length}})))

(fact "about transforming requests to answer-data"
      (fact "extracts the relevant data"
            (let [answer-data (request->answer-data {:route-params {:id (str OBJECTIVE_ID)
                                                                    :q-id (str QUESTION_ID)}
                                                     :params {:answer "the answer"}}
                                                    USER_ID)]
              (:data answer-data) => {:answer "the answer"
                                      :question-id QUESTION_ID
                                      :objective-id OBJECTIVE_ID
                                      :created-by-id USER_ID}
              (:status answer-data) => ::objective8.front-end-requests/valid
              (:report answer-data) => {}))

      (fact "reports validation errors"
            (let [answer-data (request->answer-data {:route-params {:id (str OBJECTIVE_ID)
                                                                    :q-id (str QUESTION_ID)}
                                                     :params {:answer ""}}
                                                    USER_ID)]
              (:data answer-data) => {:answer ""
                                      :question-id QUESTION_ID
                                      :objective-id OBJECTIVE_ID
                                      :created-by-id USER_ID}
              (:status answer-data) => ::objective8.front-end-requests/invalid
              (:report answer-data) => {:answer #{:length}})))

(fact "about transforming requests to comment-data"
      (fact "extracts the relevant data"
            (let [comment-data (request->comment-data {:params {:comment "the comment"
                                                                :comment-on-uri "/some/uri"}}
                                                      USER_ID)]
              (:data comment-data) => {:comment "the comment"
                                       :comment-on-uri "/some/uri"
                                       :created-by-id USER_ID}
              (:status comment-data) => ::objective8.front-end-requests/valid
              (:report comment-data) => {}))

      (tabular
       (fact "reports validation errors"
             (let [comment-data (request->comment-data {:params {:comment ?comment
                                                                 :comment-on-uri "/some/uri"}}
                                                       USER_ID)]
               (:data comment-data) => {:comment ?comment
                                        :comment-on-uri "/some/uri"
                                        :created-by-id USER_ID}
               (:status comment-data) => ::objective8.front-end-requests/invalid
               (:report comment-data) => {:comment ?error-type}))
       ?comment                 ?error-type
       ""                       #{:empty}
       (string-of-length 501)   #{:length})

      (fact "returns nil when data that is not directly provided by the user is invalid"
            (let [comment-data (request->comment-data {:params {:comment ""}}
                                                      USER_ID)]
              comment-data => nil)))

(facts "about transforming requests to writer note data"
       (fact "extracts the relevant data"
             (let [note-data (request->writer-note-data {:params {:note "the note"
                                                                  :note-on-uri "/some/uri"}}
                                                        USER_ID)]
               (:data note-data) => {:note "the note"
                                     :note-on-uri "/some/uri"
                                     :created-by-id USER_ID}
               (:status note-data) => ::objective8.front-end-requests/valid
               (:report note-data) => {}))

       (fact "reports validation errors"
             (let [note-data (request->writer-note-data {:params {:note ""
                                                                  :note-on-uri "/some/uri"}}
                                                        USER_ID)]
               (:data note-data) => {:note ""
                                     :note-on-uri "/some/uri"
                                     :created-by-id USER_ID}
               (:status note-data) => ::objective8.front-end-requests/invalid
               (:report note-data) => {:note #{:empty}}))

       (fact "returns nil when data that is not directly provided by the user is invalid"
             (let [note-data (request->writer-note-data {:params {:note "the note"}}
                                                        USER_ID)]
               note-data => nil)))
