(ns objective8.handlers.api
  (:require [clojure.tools.logging :as log]
            [ring.util.response :as response]
            [cheshire.core :as json]
            [objective8.storage.storage :as storage]
            [objective8.objectives :as objectives]
            [objective8.comments :as comments]
            [objective8.questions :as questions]
            [objective8.answers :as answers]
            [objective8.users :as users]
            [objective8.writers :as writers]
            [objective8.utils :as utils]))

(defn invalid-response [message]
  {:status 400
   :headers {"Content-Type" "application/json"}
   :body message})

(defn successful-post-response [resource-location stored-object]
  {:status 201
   :headers {"Content-Type" "application/json"
             "Location" resource-location}
   :body stored-object})

(defn find-user-by-query [request]
  (let [twitter-id (get-in request [:params :twitter])]
    (if-let [user (users/find-user-by-twitter-id twitter-id)]
      (response/content-type (response/response user) "application/json")
      (response/not-found ""))))

;; USERS
(defn post-user-profile [request]
  (let [twitter-id (get-in request [:params :twitter-id])
        email-address (get-in request [:params :email-address])
        user (users/store-user! {:twitter-id twitter-id
                                        :email-address email-address})
        resource-location (str utils/host-url "/api/v1/users/" (:_id user))]
    (successful-post-response resource-location user)))

(defn get-user [{:keys [route-params] :as request}]
  (try
    (let [id (-> (:id route-params)
                 Integer/parseInt)]
      (if-let [user (users/retrieve-user id)]
        (-> user
            response/response
            (response/content-type "application/json"))
        (response/not-found "")))
    (catch NumberFormatException e
      (log/info "Invalid route: " e)
      (invalid-response  "User id must be an integer"))))

;; OBJECTIVE
(defn post-objective [{:keys [params] :as request}]
  (try
    (let [objective (-> params
                        (select-keys [:title :goal-1 :goal-2 :goal-3 :description :end-date :created-by-id]))
          stored-objective (objectives/store-objective! objective)
          resource-location (str utils/host-url "/api/v1/objectives/" (:_id stored-objective))]
      (successful-post-response resource-location stored-objective))
    (catch Exception e
      (log/info "Error when posting objective: " e)
      (invalid-response "Invalid objective post request"))))

(defn get-objective [{:keys [route-params] :as request}]
  (try
    (let [id (-> (:id route-params)
                 Integer/parseInt)]
      (if-let [objective (objectives/retrieve-objective id)]
        (-> objective
            (update-in [:end-date] utils/date-time->iso-time-string)
            response/response
            (response/content-type "application/json"))
        (response/not-found "")))
    (catch NumberFormatException e
      (log/info "Invalid route: " e)
      (invalid-response  "Objective id must be an integer"))))

(defn get-objectives [_]
  (response/content-type (response/response (objectives/retrieve-objectives)) "application/json"))

;; COMMENT
(defn post-comment [{:keys [params] :as request}]
  (try
    (let [comment (-> params
                    (select-keys [:comment :objective-id :created-by-id]))
          stored-comment (comments/store-comment! comment)
          resource-location (str utils/host-url "/api/v1/comments/" (:_id stored-comment))]
      (successful-post-response resource-location stored-comment))
    (catch Exception e
      (log/info "Error when posting comment: " e)
      (invalid-response "Invalid comment post request"))))

(defn retrieve-comments [{:keys [route-params] :as request}]
  (try
    (let [id (-> (:id route-params)
                 Integer/parseInt)]
      (if-let [comments (comments/retrieve-comments id)]
        (-> comments
            response/response
            (response/content-type "application/json"))
        (response/not-found "")))
    (catch NumberFormatException e
      (log/info "Invalid route: " e)
      (invalid-response  "Objective id must be an integer"))))

;;QUESTIONS
(defn post-question [{:keys [route-params params] :as request}]
  (try
    (let [id (-> (:id route-params)
                 Integer/parseInt)]
      (let [question (-> params
                         (select-keys [:question :created-by-id])
                         (assoc :objective-id id))
            stored-question (questions/store-question! question)
            resource-location (str utils/host-url "/api/v1/objectives/" (:objective-id stored-question) "/questions/" (:_id stored-question))]
        (successful-post-response resource-location stored-question)))
    (catch NumberFormatException e
      (log/info "Invalid route: " e)
      (invalid-response  "Objective id must be an integer"))
    (catch Exception e
      (log/info "Error when posting question: " e)
      (invalid-response "Invalid question post request"))))

(defn check-question-matches-objective [question-id objective-id]
  (let [question (questions/retrieve-question question-id)]
    (when-not (= (:objective-id question) objective-id)
      (throw (Exception. "Question does not belong to this objective")))))


(defn get-question [{:keys [route-params] :as request}]
  (try
    (let [q-id (-> (:q-id route-params)
                   Integer/parseInt)
          objective-id (-> (:id route-params)
                           Integer/parseInt)]
      (check-question-matches-objective q-id objective-id) 
      (if-let [question (questions/retrieve-question q-id)]
        (-> question
            response/response
            (response/content-type "application/json"))
        (response/not-found "")))
    (catch NumberFormatException e
      (log/info "Invalid route: " e)
      (invalid-response  "Question id must be an integer"))
    (catch Exception e
      (log/info "Invalid route: " e)
      (invalid-response "Invalid question request for this objective")))) 

(defn retrieve-questions [{:keys [route-params] :as request}]
  (try
    (let [objective-id (-> (:id route-params)
                            Integer/parseInt)]
      (if-let [questions (questions/retrieve-questions objective-id)]
        (-> questions
            response/response
            (response/content-type "application/json"))
        (response/not-found "")))
    (catch NumberFormatException e
      (log/info "Invalid route: " e)
      (invalid-response "Objective id must be an integer"))))

;;ANSWERS
(defn post-answer [{:keys [route-params params] :as request}]
  (try
    (let [q-id (-> (:q-id route-params)
                   Integer/parseInt)
          objective-id (-> (:id route-params)
                           Integer/parseInt)] 
      (check-question-matches-objective q-id objective-id) 
      (let [answer (-> params
                       (select-keys [:answer :created-by-id])
                       (assoc :objective-id objective-id)
                       (assoc :question-id q-id)) 
            stored-answer (answers/store-answer! answer) 
            resource-location (str utils/host-url
                                   "/api/v1/objectives/" (:objective-id stored-answer)
                                   "/questions/" (:question-id stored-answer)
                                   "/answers/" (:_id stored-answer))]
        (successful-post-response resource-location stored-answer)))
    (catch NumberFormatException e
      (log/info "Invalid route: " e)
      (invalid-response "Objective and question ids must be integers"))
    (catch Exception e
      (log/info "Error when posting answer: " e)
      (invalid-response "Invalid answer post request"))))

(defn retrieve-answers [{:keys [route-params] :as request}]
  (try
    (let  [q-id (-> (:q-id route-params)
                     Integer/parseInt)
           objective-id (-> (:id route-params)
                            Integer/parseInt)]
      (check-question-matches-objective q-id objective-id)
      (if-let [answers (answers/retrieve-answers q-id)]
        (-> answers
            response/response
            (response/content-type "application/json"))
        (response/not-found "")))
    (catch NumberFormatException e
      (log/info "Invalid route: " e)
      (invalid-response "Objective and question ids must be integers"))
    (catch Exception e
      (log/info "Invalid route: " e)
      (invalid-response "Invalid answer request for this objective"))))

;;WRITERS
(defn post-invitation [{:keys [route-params params]}]
  (try
    (let [objective-id (-> (:id route-params)
                           Integer/parseInt)
          invitation (-> params
                     (select-keys [:writer-name :reason :invited-by-id])
                     (assoc :objective-id objective-id))
          stored-invitation (writers/store-invitation! invitation)
          resource-location (str utils/host-url
                                 "/api/v1/objectives/" (:objective-id stored-invitation)
                                 "/writers/invitations/" (:_id stored-invitation))]
      (successful-post-response resource-location stored-invitation))
  (catch NumberFormatException e
    (log/info "Invalid route: " e)
    (invalid-response "Objective id must be an integer"))
  (catch Exception e
    (log/info "Error when posting an invitation: " e)
    (invalid-response "Invalid invitation post request for this objective"))))

(defn get-invitation [{{uuid :uuid} :params}]
  (try
    (if-let [invitation (writers/retrieve-invitation-by-uuid uuid)]
      (-> invitation
          response/response
          (response/content-type "application/json"))
      (response/not-found (str "No invitations exist with uuid: " uuid)))
    (catch Exception e
      (log/info "Error when retrieving invitation: " e)
      (invalid-response (str "Error when retrieving invitation with uuid " uuid)))))

(defn post-invitation-response [{{objective-id :id invitation-id :inv-id} :route-params
                                 params :params}]
  (let [invitation-response (-> (select-keys params [:invitee-id :response :uuid])
                                (assoc :objective-id (Integer/parseInt objective-id) 
                                       :invitation-id (Integer/parseInt invitation-id)))]
  (let [candidate-writer (writers/accept-invitation invitation-response)
        resource-location (str utils/host-url "/api/v1/objectives/" objective-id
                               "/candidate-writers/" (:_id candidate-writer))]
    (successful-post-response resource-location candidate-writer))))

(defn retrieve-candidates [{:keys [route-params params]}]
  (try
    (let [objective-id (-> (:id route-params)
                           Integer/parseInt)
          candidates (writers/retrieve-candidates objective-id)]
      (-> candidates
          response/response
          (response/content-type "application/json")))
  (catch NumberFormatException e
    (log/info "Invalid route: " e)
    (invalid-response "Objective id must be an integer"))
  (catch Exception e
    (log/info "Error when retrieving candidates: " e)
    (invalid-response "Invalid candidates get request for this objective"))))
