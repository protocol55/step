(ns protocol55.step.alpha.specs
  (:require [clojure.spec.alpha :as s]))

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

(s/def ::data-def symbol?)

(s/def ::stepdef-opts (s/keys :opt-un [::extra-defs ::data-def]))

(s/def ::stepdef-args
  (s/cat :k keyword? :opts (s/? ::stepdef-opts)
         :forms (s/* (s/and any? ::form))))

(s/fdef protocol55.step.alpha/step*
  :args ::step*-args
  :ret any?)

(s/fdef protocol55.step.alpha/stepdef
  :args ::stepdef-args
  :ret any?)
