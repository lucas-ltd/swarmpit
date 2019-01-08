(ns swarmpit.component.stack.info
  (:require [material.icon :as icon]
            [material.components :as comp]
            [material.component.form :as form]
            [material.component.chart :as chart]
            [swarmpit.component.message :as message]
            [material.component.label :as label]
            [swarmpit.component.state :as state]
            [swarmpit.component.mixin :as mixin]
            [swarmpit.component.progress :as progress]
            [swarmpit.component.common :as common]
            [material.component.list.basic :as list]
            [swarmpit.component.service.list :as services]
            [swarmpit.component.network.list :as networks]
            [swarmpit.component.volume.list :as volumes]
            [swarmpit.component.config.list :as configs]
            [swarmpit.component.secret.list :as secrets]
            [swarmpit.ajax :as ajax]
            [swarmpit.url :refer [dispatch!]]
            [swarmpit.routes :as routes]
            [swarmpit.docker.utils :as utils]
            [clojure.string :refer [includes?]]
            [sablono.core :refer-macros [html]]
            [clojure.contrib.inflect :as inflect]
            [rum.core :as rum]))

(enable-console-print!)

(defn- stack-services-handler
  [stack-name]
  (ajax/get
    (routes/path-for-backend :stack-services {:name stack-name})
    {:state      [:loading?]
     :on-success (fn [{:keys [response]}]
                   (state/update-value [:services] response state/form-value-cursor))}))

(defn- stack-networks-handler
  [stack-name]
  (ajax/get
    (routes/path-for-backend :stack-networks {:name stack-name})
    {:on-success (fn [{:keys [response]}]
                   (state/update-value [:networks] response state/form-value-cursor))}))

(defn- stack-volumes-handler
  [stack-name]
  (ajax/get
    (routes/path-for-backend :stack-volumes {:name stack-name})
    {:on-success (fn [{:keys [response]}]
                   (state/update-value [:volumes] response state/form-value-cursor))}))

(defn- stack-configs-handler
  [stack-name]
  (ajax/get
    (routes/path-for-backend :stack-configs {:name stack-name})
    {:on-success (fn [{:keys [response]}]
                   (state/update-value [:configs] response state/form-value-cursor))}))

(defn- stack-secrets-handler
  [stack-name]
  (ajax/get
    (routes/path-for-backend :stack-secrets {:name stack-name})
    {:on-success (fn [{:keys [response]}]
                   (state/update-value [:secrets] response state/form-value-cursor))}))

(defn- stackfile-handler
  [stack-name]
  (ajax/get
    (routes/path-for-backend :stack-file {:name stack-name})
    {:on-success (fn [{:keys [response]}]
                   (state/update-value [:stackfile] response state/form-value-cursor))
     :on-error   (fn [_])}))

(defn- delete-stack-handler
  [stack-name]
  (ajax/delete
    (routes/path-for-backend :stack-delete {:name stack-name})
    {:on-success (fn [_]
                   (dispatch!
                     (routes/path-for-frontend :stack-list))
                   (message/info
                     (str "Stack " stack-name " has been removed.")))
     :on-error   (fn [{:keys [response]}]
                   (message/error
                     (str "Stack removing failed. " (:error response))))}))

(defn- redeploy-stack-handler
  [stack-name]
  (message/info
    (str "Stack " stack-name " redeploy triggered."))
  (ajax/post
    (routes/path-for-backend :stack-redeploy {:name stack-name})
    {:on-success (fn [_]
                   (message/info
                     (str "Stack " stack-name " redeploy finished.")))
     :on-error   (fn [{:keys [response]}]
                   (message/error
                     (str "Stack redeploy failed. " (:error response))))}))

(defn- rollback-stack-handler
  [stack-name]
  (message/info
    (str "Stack " stack-name " rollback triggered."))
  (ajax/post
    (routes/path-for-backend :stack-rollback {:name stack-name})
    {:on-success (fn [_]
                   (message/info
                     (str "Stack " stack-name " rollback finished.")))
     :on-error   (fn [{:keys [response]}]
                   (message/error
                     (str "Stack rollback failed. " (:error response))))}))

(defn form-actions
  [stack-name stackfile]
  [{:onClick #(dispatch! (routes/path-for-frontend :stack-compose {:name stack-name}))
    :icon    (comp/svg icon/edit-path)
    :name    "Edit stack"}
   {:onClick  #(redeploy-stack-handler stack-name)
    :disabled (not (some? (:spec stackfile)))
    :more     true
    :icon     (comp/svg icon/redeploy-path)
    :name     "Redeploy stack"}
   {:onClick  #(rollback-stack-handler stack-name)
    :disabled (not (some? (:previousSpec stackfile)))
    :more     true
    :icon     (comp/svg icon/rollback-path)
    :name     "Rollback stack"}
   {:onClick #(delete-stack-handler stack-name)
    :icon    (comp/svg icon/trash-path)
    :name    "Delete stack"}])

;(defn- stack-render-item
;  [stack-name name-key default-render-item]
;  (fn [item row]
;    (case (key item)
;      :stack (if (not= stack-name (val item))
;               (label/info "external"))
;      (if (= name-key (key item))
;        (if (= stack-name (:stack row))
;          (utils/trim-stack stack-name (val item))
;          (val item))
;        (default-render-item item row)))))

(rum/defc form-services-graph < rum/static [services]
  (let [data (->> services
                  (map (fn [service]
                         (if (= "running" (:state service))
                           {:name  (:serviceName service)
                            :value 1
                            :color "#43a047"
                            :state (:state service)}
                           {:name  (:serviceName service)
                            :value 1
                            :color "#6c757d"
                            :state (:state service)})))
                  (into []))]
    (chart/pie
      data
      (str (count services) " " (inflect/pluralize-noun (count services) "service"))
      "Swarmpit-service-replicas-graph"
      "sservices-pie"
      {:formatter (fn [value name props]
                    (.-state (.-payload props)))})))

(rum/defc form-general < rum/static [stack-name stackfile services]
  (let []
    (comp/card
      {:className "Swarmpit-form-card"
       :key       "fgc"}
      (comp/card-header
        {:title     stack-name
         :className "Swarmpit-form-card-header"
         :key       "fgch"
         :action    (common/actions-menu
                      (form-actions stack-name stackfile)
                      :stackGeneralMenuAnchor
                      :stackGeneralMenuOpened)})
      (comp/card-content
        {:key "fgccc"}
        (rum/with-key (form-services-graph services) "fgcccg"))
      (comp/divider
        {:key "fgd"})
      (comp/card-content
        {:key   "fgcccf"
         :style {:paddingBottom "16px"}}
        (form/item-id stack-name)))))

(rum/defc form-services < rum/static [stack-name services]
  (comp/card
    {:className "Swarmpit-card"
     :key       "fsc"}
    (comp/card-header
      {:className "Swarmpit-table-card-header"
       :key       "fsch"
       :title     "Services"})
    (comp/card-content
      {:className "Swarmpit-table-card-content"
       :key       "fscc"}
      (rum/with-key
        (list/responsive
          services/render-metadata
          (sort-by :serviceName services)
          services/onclick-handler) "fsccrl"))))

(rum/defc form-networks < rum/static [stack-name networks]
  (comp/card
    {:className "Swarmpit-card"
     :key       "fnc"}
    (comp/card-header
      {:className "Swarmpit-table-card-header"
       :key       "fnch"
       :title     "Networks"})
    (if (empty? networks)
      (comp/card-content
        {:key "fvcce"}
        (html [:div "No networks in stack."]))
      (comp/card-content
        {:className "Swarmpit-table-card-content"
         :key       "fncc"}
        (rum/with-key
          (list/responsive
            networks/render-metadata
            (sort-by :networkName networks)
            networks/onclick-handler) "fnccrl")))))

(rum/defc form-volumes < rum/static [stack-name volumes]
  (comp/card
    {:className "Swarmpit-card"
     :key       "fvc"}
    (comp/card-header
      {:className "Swarmpit-table-card-header"
       :key       "fvch"
       :title     "Volumes"})
    (if (empty? volumes)
      (comp/card-content
        {:key "fvcce"}
        (html [:div "No volumes in stack."]))
      (comp/card-content
        {:className "Swarmpit-table-card-content"
         :key       "fvcc"}
        (rum/with-key
          (list/responsive
            volumes/render-metadata
            (sort-by :volumeName volumes)
            volumes/onclick-handler) "fvccrl")))))

(rum/defc form-configs < rum/static [stack-name configs]
  (comp/card
    {:className "Swarmpit-card"
     :key       "fcc"}
    (comp/card-header
      {:className "Swarmpit-table-card-header"
       :key       "fcch"
       :title     "Configs"})
    (if (empty? configs)
      (comp/card-content
        {:key "fccce"}
        (html [:div "No configs in stack."]))
      (comp/card-content
        {:className "Swarmpit-table-card-content"
         :key       "fccc"}
        (rum/with-key
          (list/list
            (:list configs/render-metadata)
            (sort-by :configName configs)
            configs/onclick-handler) "fcccrl")))))

(rum/defc form-secrets < rum/static [stack-name secrets]
  (comp/card
    {:className "Swarmpit-card"
     :key       "fsec"}
    (comp/card-header
      {:className "Swarmpit-table-card-header"
       :key       "fsech"
       :title     "Secrets"})
    (if (empty? secrets)
      (comp/card-content
        {:key "fsecce"}
        (html [:div "No secrets in stack."]))
      (comp/card-content
        {:className "Swarmpit-table-card-content"
         :key       "fsecc"}
        (rum/with-key
          (list/list
            (:list secrets/render-metadata)
            (sort-by :secretName secrets)
            secrets/onclick-handler) "fseccrl")))))

(defn- init-form-state
  []
  (state/set-value {:menu?    false
                    :loading? true} state/form-state-cursor))

(def mixin-init-form
  (mixin/init-form
    (fn [{{:keys [name]} :params}]
      (init-form-state)
      (stack-services-handler name)
      (stack-networks-handler name)
      (stack-volumes-handler name)
      (stack-configs-handler name)
      (stack-secrets-handler name)
      (stackfile-handler name))))

(defn form-general-grid [stack-name stackfile services]
  (comp/grid
    {:item true
     :key  "sgg"
     :xs   12}
    (rum/with-key
      (form-general stack-name stackfile services) "sggfg")))

(defn form-services-grid [stack-name services]
  (comp/grid
    {:item true
     :key  "ssg"
     :xs   12}
    (rum/with-key
      (form-services stack-name services) "ssgfs")))

(defn form-networks-grid [stack-name networks]
  (comp/grid
    {:item true
     :key  "sng"
     :xs   12}
    (rum/with-key
      (form-networks stack-name networks) "sngfn")))

(defn form-secrets-grid [stack-name secrets]
  (comp/grid
    {:item true
     :key  "sseg"
     :xs   12}
    (rum/with-key
      (form-secrets stack-name secrets) "ssegfs")))

(defn form-configs-grid [stack-name configs]
  (comp/grid
    {:item true
     :key  "scg"
     :xs   12}
    (rum/with-key
      (form-configs stack-name configs) "scgfc")))

(defn form-volumes-grid [stack-name volumes]
  (comp/grid
    {:item true
     :key  "svg"
     :xs   12}
    (rum/with-key
      (form-volumes stack-name volumes) "svgfv")))


(rum/defc form-info < rum/static [stack-name
                                  {:keys [services networks volumes configs secrets stackfile]}]
  (comp/mui
    (html
      [:div.Swarmpit-form
       [:div.Swarmpit-form-context
        [:div {:className "Swarmpit-section-desktop"
               :key       "sfisd"}
         (comp/grid
           {:container true
            :spacing   40}
           (comp/grid
             {:item true
              :key  "slg"
              :sm   6
              :md   6
              :lg   4}
             (comp/grid
               {:container true
                :spacing   40}
               (form-general-grid stack-name stackfile services)
               (form-secrets-grid stack-name secrets)
               (form-configs-grid stack-name configs)))
           (comp/grid
             {:item true
              :key  "srg"
              :sm   6
              :md   6
              :lg   8}
             (comp/grid
               {:container true
                :spacing   40}
               (form-services-grid stack-name services)
               (form-networks-grid stack-name networks)
               (form-volumes-grid stack-name volumes))))]
        [:div {:className "Swarmpit-section-mobile"
               :key       "sfism"}
         (comp/grid
           {:container true
            :spacing   40}
           (form-general-grid stack-name stackfile services)
           (form-services-grid stack-name services)
           (form-networks-grid stack-name networks)
           (form-volumes-grid stack-name volumes)
           (form-secrets-grid stack-name secrets)
           (form-configs-grid stack-name configs))]]])))

(rum/defc form < rum/reactive
                 mixin-init-form
                 mixin/subscribe-form [{{:keys [name]} :params}]
  (let [state (state/react state/form-state-cursor)
        item (state/react state/form-value-cursor)]
    (progress/form
      (:loading? state)
      (form-info name item))))
