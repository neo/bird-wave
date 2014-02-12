(ns bird-man.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [io.pedestal.service.interceptor :refer [defon-request defbefore defafter]]
              [io.pedestal.service.log :as log]
              [hiccup.page :as page]
              [hiccup.form :as form]
              [ring.util.response :as ring-resp]
              [ring.util.codec :as ring-codec]
              [datomic.api :as d :refer (q db)]))

(defn home-page [request]
  (ring-resp/response
    (page/html5
      {:lang "en"}
      [:head
       [:meta {:charset "utf-8"}]
       [:title "Frequency Map"]]
      [:body
       [:script {:src "/javascript/goog/base.js"}]
       [:script {:src "/javascript/d3.v3.js"}]
       [:script {:src "/javascript/queue.js"}]
       [:script {:src "/javascript/topojson.js"}]
       [:script {:src "/javascript/client-dev.js"}]
       (page/include-css "/stylesheets/main.css")
       [:script "goog.require('bird_man.client');"]
       [:script "bird_man.client.start_client();"]])))

(defonce datomic-connection nil)

(defn add-datomic-conn [request]
  (assoc request :datomic-conn datomic-connection))

(defon-request datomic-conn
  "Add a conn reference to each request"
  add-datomic-conn)

(defbefore log-request [context]
  (log/info :request (:request context))
  context)

(defafter log-response [context]
  (log/info :response (:response context))
  context)

(defn species-index [{conn :datomic-conn :as request}]
  (ring-resp/response
   (map zipmap
        (repeat [:name :count])
        (d/q '[:find ?name (sum ?count)
               :where [?e :sighting/common-name ?name]
               [?e :sighting/count ?count]]
             (d/db conn)))))

(defn countywise-frequencies [{conn :datomic-conn :as request}]
  (ring-resp/response
    (let [common-name (ring-codec/url-decode (get-in request [:path-params :common-name]))]
      (map zipmap
           (repeat [:county :state :total :sightings])
           (d/q '[:find ?county ?state (sum ?count) (count ?e)
           :in $ ?name
           :where
           [?e :sighting/common-name ?name]
           [?e :sighting/state-code ?state]
           [?e :sighting/count ?count]
           [?e :sighting/county ?county]]
         (d/db conn) common-name)))))

(defroutes routes
  [[["/" {:get home-page}
     ;; Set default interceptors for /about and any other paths under /
     ^:interceptors [(body-params/body-params) datomic-conn bootstrap/html-body]
     ["/species" {:get species-index}
       ^:interceptors [bootstrap/json-body]
      ["/:common-name" {:get countywise-frequencies}]]]]])

;; Consumed by bird-man.server/create-server
;; See bootstrap/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; :bootstrap/interceptors []
              ::bootstrap/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::bootstrap/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::bootstrap/resource-path "/public"

              ;; Either :jetty or :tomcat (see comments in project.clj
              ;; to enable Tomcat)
              ;;::bootstrap/host "localhost"
              ::bootstrap/type :jetty
              ::bootstrap/port 8080})
