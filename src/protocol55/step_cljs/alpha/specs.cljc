(ns protocol55.step-cljs.alpha.specs
  (:require [clojure.spec.alpha :as s]
            #?(:clj [protocol55.step-cljs.alpha :as step]))
  #?(:cljs
     (:require-macros [protocol55.step-cljs.alpha :as step])))

(s/def ::form
        (s/cat :state keyword?
               :intent (s/* (s/cat :action keyword?
                                   :states' (s/coll-of keyword?)))))

(s/def ::forms
  (s/coll-of ::form))

(s/def ::ristrict #{:no-state' :only-states :only-action})

(s/def ::step*-args
  (s/cat :forms ::forms
         :kwargs (s/keys* :opt-un [::ristrict])))

(s/def ::extra-defs (s/and (s/coll-of keyword?)
                           #(even? (count %))))

(s/def ::stepdef-opts (s/keys :opt-un [::extra-defs]))

(s/def ::stepdef-args
  (s/cat :k keyword? :opts (s/? ::stepdef-opts)
         :forms (s/* (s/and any? ::form))))

(s/fdef step/step*
  :args ::step*-args
  :ret any?)

(s/fdef step/stepdef
  :args ::stepdef-args
  :ret any?)
