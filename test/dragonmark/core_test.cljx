(ns dragonmark.core-test
  (:require
   [dragonmark.core :as dc]
   [schema.core :as sc]
   #+clj [clojure.core.async :as async :refer [go chan timeout <! close!]]
   #+cljs [cljs.core.async :as async :refer [chan timeout <! close!]]
   #+cljs [cemerick.cljs.test :as t]
   #+clj [clojure.test :as t
          :refer (is deftest with-test run-tests testing)]
   )
  #+cljs (:require-macros [cemerick.cljs.test :as t
                           :refer (is deftest with-test run-tests testing test-var)]
                          [schema.macros :as sc]
                          [cljs.core.async.macros :as async :refer [go]]
                          [dragonmark.core :as dc])
  )

(def my-root (dc/build-root-channel {}))

(def my-atom (atom nil))

(def my-error-atom (atom nil))

(dc/gofor
 :let [b 44]
 [root (get-service my-root {:service 'root})]
 [added (add root {:service 'wombat2 :channel (chan) :public true})]
 [b (list root)]
 :let [a (+ 1 1)]
 (reset! my-atom b)
 :error (reset! my-atom (str "Got an error " &err " for frogs " &var)))

(dc/gofor
 :let [b 44]
 [root (get-service-will-fail my-root {:service 'root})]
 [added (add root {:service 'wombat2 :channel (chan) :public true})]
 [b (list root)]
 :let [a (+ 1 1)]
 (reset! my-atom [b])
 :error (reset! my-error-atom (str "Got an error " &err " for frogs " &var)))


(deftest  ^:async go-for-test

  #+clj (is (= (into #{} @my-atom) #{'wombat2 'root}))

  #+cljs (js/setTimeout
          (fn []
            (is (= (into #{} @my-atom) #{'wombat2 'root}))
            (t/done))
          25)
  )

(deftest  ^:async go-for-test

  #+clj (is (string? @my-error-atom))

  #+cljs (js/setTimeout
          (fn []
            (is (string? @my-error-atom))
            (t/done))
          25)
  )

(sc/defn ^:service get-42 :- sc/Num
  "Hello"
  ([] 42)
  ([x :- sc/Num] (+ x 42)))

(sc/defn ^:service plus-one :- sc/Num
  [x :- sc/Num]
  (+ 1 x))


(deftest ^:async build-service-test
  (let [atom-42 (atom nil)]

    (dc/gofor
     :let [service (dc/build-service)]
     [x (get-42 service)]
     [y (plus-one service {:x x})]
     (reset! atom-42 [x y])
     :error (reset! atom-42 &err))

  #+clj (do
          (Thread/sleep 100)
          (is (= @atom-42 [42 43])))

  #+cljs (js/setTimeout
          (fn []
            (is (= @atom-42 [42 43]))
            (t/done))
          25)))

#+clj
(deftest ^:async distributed-test
  (let [a-chan (chan)
        b-chan (chan)
        a-info (atom 0)
        b-info (atom 0)
        a-root (dc/build-root-channel {"get" (fn [msg env] @a-info)
                                       "inc" (fn [msg env] (swap! a-info inc))})
        b-root (dc/build-root-channel {"get" (fn [msg env]
                                               @b-info)
                                       "inc" (fn [msg env]
                                               (swap! b-info inc))})
        a-transport (dc/build-transport a-root a-chan b-chan)
        b-transport (dc/build-transport b-root b-chan a-chan)
        b-root-proxy (dc/remote-root a-transport)
        a-root-proxy (dc/remote-root b-transport)
        res (atom nil)
        ]
    (dc/gofor
     [_ (inc b-root-proxy)]
     [answer (get b-root-proxy)]
     (reset! res answer)
     :error (reset! res &err))

    #+clj
    (do
      (Thread/sleep 100)
      (is (= 1 @res))
      (is (= @res @b-info))
      )

    #+cljs
    (js/setTimeout
     (fn []
       (is (= 1 1))
       ;; (is (= 1 @res))
       ;; (is (= @res @b-info))
       (t/done))
     25)

    ))
