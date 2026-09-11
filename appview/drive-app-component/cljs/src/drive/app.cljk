(ns drive.app
  "drive-app-component appview — reagent + re-frame, view built from
  jp-go-dds (デジタル庁デザインシステム) hiccup.

  Faithful port of the previous SvelteKit scaffold's status page
  (`svelte/src/App.svelte`, rendered via `svelte/src/routes/+page.svelte`,
  which did nothing but `<App />` — 111 lines total across both files):
  a static preview card for this Worker's companion UI — the
  `drive.etzhayyim.com` eyebrow, a `Workspace Drive` heading, an inert
  `Connect` button (the Svelte source had no `on:click`; this port keeps
  it inert too — adding a handler would be inventing behaviour that
  never existed), and three status cards (Sync/Ready, Recent/0 files,
  Security/E2EE path) with their original descriptive copy verbatim.
  Nothing here is invented and nothing is simplified away.

  Unlike the sibling `meet-mcp-component` migration, this Svelte tree
  had no `+server.ts` route — there was no XRPC proxy living under
  `svelte/src/routes/` to preserve or note. `appview/drive-app-component/
  src/app.ts` (the live Worker facade: /health, /oauth/callback, the
  `/xrpc/com.etzhayyim.apps.drive.*` → dispatcher proxy, and the
  `env.ASSETS.fetch` fallback this bundle is served through) is
  untouched by this migration and is out of scope for a frontend port.

  `public/index.html`'s inlined <style> was produced once, at authoring
  time, by `jp-go-dds.page/->page` running on the JVM (via this deps.edn's
  jp-go-dds git/sha), concatenating the vendored `dds.css` with
  `jp-go-dds.core/ext-css` — exactly what `jp-go-dds.page/page` composes
  for its own <style> block. This namespace only requires
  `jp-go-dds.core` — the browser bundle does not need `jp-go-dds.page` or
  `html.core` at runtime; those are JVM-only tools used to author the
  static shell once. Regenerate that shell (e.g. if jp-go-dds's core
  components or ext-rules change) with:

    (require '[jp-go-dds.page :as page] '[clojure.java.io :as io])
    (spit \"public/index.html\"
          (page/->page {:title \"drive-app-component\"
                         :lang \"ja\"
                         :description \"drive-app-component — drive.etzhayyim.com thin edge facade companion UI (reagent + re-frame + jp-go-dds).\"
                         :css (slurp (io/resource \"jp_go_dds/dds.css\"))}
                        [:div {:id \"app\"} \"drive-app-component loading…\"]
                        [:script {:src \"js/app.js\"}]))"
  (:require [reagent.dom :as rdom]
            [re-frame.core :as rf]
            [jp-go-dds.core :as dds]))

;; -- db ------------------------------------------------------------------
;;
;; Same facts `App.svelte` held: the eyebrow/title pair, the inert
;; Connect button's label, and the three status cards' label/value/
;; description triples, in source order.

(def default-db
  {:app/eyebrow "drive.etzhayyim.com"
   :app/title "Workspace Drive"
   :app/connect-label "Connect"
   :app/status
   [{:status/label "Sync"
     :status/value "Ready"
     :status/description "Google Workspace connector and Zeebe jobs are exposed through the edge facade."}
    {:status/label "Recent"
     :status/value "0 files"
     :status/description "No local account is connected in this preview session."}
    {:status/label "Security"
     :status/value "E2EE path"
     :status/description "File operations stay behind the dispatcher and internal trust boundary."}]})

(rf/reg-event-db
 :initialize-db
 (fn [_ _] default-db))

(rf/reg-sub :app/eyebrow (fn [db _] (:app/eyebrow db)))
(rf/reg-sub :app/title (fn [db _] (:app/title db)))
(rf/reg-sub :app/connect-label (fn [db _] (:app/connect-label db)))
(rf/reg-sub :app/status (fn [db _] (:app/status db)))

;; -- view ------------------------------------------------------------------

(defn status-card [{:status/keys [label value description]}]
  (dds/card
   [:p {:class "dds-ext-lead"} label]
   [:strong value]
   [:p description]))

(defn app-view []
  (let [eyebrow        @(rf/subscribe [:app/eyebrow])
        title          @(rf/subscribe [:app/title])
        connect-label  @(rf/subscribe [:app/connect-label])
        status         @(rf/subscribe [:app/status])]
    (dds/container

     [:section {:class "dds-ext-section"}
      (dds/row
       [:div
        [:p {:class "dds-ext-lead"} eyebrow]
        (dds/heading 1 title)]
       (dds/button connect-label))]

     [:section {:class "dds-ext-section" :aria-label "Drive status"}
      (into (dds/grid {:min "220px"})
            (map status-card status))])))

;; -- mount -------------------------------------------------------------------

(defn render []
  (rdom/render [app-view] (.getElementById js/document "app")))

(defn ^:export main []
  (rf/dispatch-sync [:initialize-db])
  (render))
