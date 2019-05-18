(ns protocol55.step.tests.alpha
  (:require #?(:clj  [protocol55.step.alpha :refer [stepdef]]
               :cljs [protocol55.step-cljs.alpha :refer [stepdef]])
            #?(:clj  protocol55.step.alpha.specs
               :cljs protocol55.step-cljs.alpha.specs)
            [protocol55.step.state.alpha :as state]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as st]
            [clojure.spec.gen.alpha :as gen]
            [clojure.test :refer [deftest is]]))

(st/instrument)

(s/def ::drink (s/tuple #{:drink}))
(s/def ::fill (s/tuple #{:fill}))

(s/def ::v (state/cases :empty zero? :in-between (s/int-in 1 10) :full #{10}))

;; Qualified

(s/def ::state (s/keys :req [::v]))

(s/def ::in-between-state (state/produce ::state :of {::v :in-between}))
(s/def ::empty-state (state/produce ::state :of {::v :empty}))
(s/def ::full-state (state/produce ::state :of {::v :full}))

(stepdef ::step
  {:extra-defs [::state-action :no-state'
                ::states       :only-states
                ::action       :only-action]
   :data-def step-data}
  (::in-between-state
    ::drink (::in-between-state ::empty-state)
    ::fill  (::in-between-state ::full-state))
  (::empty-state
    ::fill (::in-between-state))
  (::full-state
    ::drink (::in-between-state)))

(defn next-state [state action]
  (case (first action)
    :drink (update state ::v dec)
    :fill (update state ::v inc)))

(s/fdef next-state
        :args ::state-action
        :ret ::state)

;; Unqualified

(s/def ::state-un (s/keys :req-un [::v]))

(s/def ::in-between-state-un (state/produce ::state-un :of-un {::v :in-between}))
(s/def ::empty-state-un (state/produce ::state-un :of-un {::v :empty}))
(s/def ::full-state-un (state/produce ::state-un :of-un {::v :full}))

(stepdef ::step-un
  {:extra-defs [::state-action-un :no-state']}
  (::in-between-state-un
    ::drink (::in-between-state-un ::empty-state-un)
    ::fill  (::in-between-state-un ::full-state-un))
  (::empty-state-un
    ::fill (::in-between-state-un))
  (::full-state-un
    ::drink (::in-between-state-un)))

(defn next-state-un [state action]
  (case (first action)
    :drink (update state :v dec)
    :fill (update state :v inc)))

(s/fdef next-state-un
        :args ::state-action-un
        :ret ::state-un)

(deftest test-step
  (is (s/valid? ::step [{::v 0} [:fill] {::v 1}]))
  (is (not (s/valid? ::step [{::v 0} [:drink] {::v 1}]))))

(deftest test-step-un
  (is (s/valid? ::step-un [{:v 0} [:fill] {:v 1}]))
  (is (not (s/valid? ::step-un [{:v 0} [:drink] {:v 1}]))))

(deftest test-extra-defs
  (is (s/valid? ::state-action [{::v 0} [:fill]]))
  (is (not (s/valid? ::state-action [{::v 0} [:drink]])))
  (is (s/valid? ::states [{::v 0} {::v 1}]))
  (is (not (s/valid? ::states [{::v 0} {::v 0}]))))

(deftest test-data-def
  (is (= step-data
         {::in-between-state
          {::drink #{::in-between-state ::empty-state}
           ::fill #{::in-between-state ::full-state}}

          ::empty-state
          {::fill #{::in-between-state}}

          ::full-state
          {::drink #{::in-between-state}}})))

(defn passes-check? [results]
  (= 1 (:check-passed (st/summarize-results results))))

(deftest test-check
  (is (passes-check? (st/check `next-state))))

(deftest test-check-un
  (is (passes-check? (st/check `next-state-un))))
