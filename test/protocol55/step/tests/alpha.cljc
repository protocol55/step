(ns protocol55.step.tests.alpha
  (:require #?(:clj  [protocol55.step.alpha :refer [stepdef]]
               :cljs [protocol55.step-cljs.alpha :refer [stepdef]])
            #?(:clj  protocol55.step.alpha.specs
               :cljs protocol55.step-cljs.alpha.specs)
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]
            [clojure.spec.gen.alpha :as gen]
            [clojure.test :refer [deftest is]]))

(st/instrument)

(s/def ::drink (s/tuple #{:drink}))
(s/def ::fill (s/tuple #{:fill}))

(s/def :in-between/v (s/int-in 1 10))
(s/def :in-between/state (s/keys :req-un [:in-between/v]))

(s/def :empty/v #{0})
(s/def :empty/state (s/keys :req-un [:empty/v]))

(s/def :full/v #{10})
(s/def :full/state (s/keys :req-un [:full/v]))

(s/def ::event (s/or :drink ::drink :fill ::fill))

(s/def ::state (s/or :in-between :in-between/state
                     :empty :empty/state
                     :full :full/state))
(stepdef ::step
  {:extra-defs [::state-action :no-state'
                ::states       :only-states
                ::action       :only-action]
   :data-def step-data}
  (:in-between/state
    ::drink (:in-between/state :empty/state)
    ::fill  (:in-between/state :full/state))
  (:empty/state
    ::fill (:in-between/state))
  (:full/state
    ::drink (:in-between/state)))

(defn next-state [state action]
  (case (first action)
    :drink (update state :v dec)
    :fill (update state :v inc)))

(s/fdef next-state
        :args ::state-action
        :ret ::state)

(deftest test-step
  (is (s/valid? ::step [{:v 0} [:fill] {:v 1}]))
  (is (not (s/valid? ::step [{:v 0} [:drink] {:v 1}]))))

(deftest test-extra-defs
  (is (s/valid? ::state-action [{:v 0} [:fill]]))
  (is (not (s/valid? ::state-action [{:v 0} [:drink]])))
  (is (s/valid? ::states [{:v 0} {:v 1}]))
  (is (not (s/valid? ::states [{:v 0} {:v 0}]))))

(deftest test-data-def
  (is (= step-data
         {:in-between/state
          {::drink #{:in-between/state :empty/state}
           ::fill #{:in-between/state :full/state}}

          :empty/state
          {::fill #{:in-between/state}}

          :full/state
          {::drink #{:in-between/state}}})))

(defn passes-check? [results]
  (= 1 (:check-passed (st/summarize-results results))))

(deftest test-check
  (is (passes-check? (st/check `next-state))))
