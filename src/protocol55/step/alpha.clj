(ns protocol55.step.alpha
  (:require [clojure.spec.alpha :as s]))

(defn- mapify-forms
  [forms]
  (->> forms
       (reduce (fn [m [state & {:as action->states'}]]
                 (assoc m state
                        (into {} (map (juxt (comp first)
                                            (comp set second)))
                              action->states')))
               {})))

(defn step*
  [forms & {:keys [restrict]}]
  `(s/or ~@(mapcat
             (fn [[state & {:as action->states'}]]
               (let [state-qualifier state]
                 `(~state-qualifier
                  (s/or ~@(mapcat
                          (fn [[action states']]
                            `(~action
                              (s/or
                                ~@(mapcat
                                    (fn [state']
                                      (let [state'-qualifier state']
                                        `(~state'-qualifier
                                          ~(case restrict
                                             :no-state'
                                             `(s/tuple ~state ~action)
                                             :only-states
                                             `(s/tuple ~state ~state')
                                             :only-action
                                             `(s/tuple ~action)
                                             `(s/tuple ~state ~action ~state')))))
                                    states'))))
                          action->states')))))
             forms)))

(defmacro stepdef
  "Defines a spec that validates a step as-per the transitions
  specified in forms. A step is a tuple of [state action state'].

  Forms are of the shape:

  (:in-between/state
    ::drink (:in-between/state :empty/state)
    ::fill  (:in-between/state :full/state))

  where all keywords are registered specs.

  Optionally, the second argument can be a map of options that accepts the
  keys :extra-defs and :data-def, detailed below.

  :extra-defs - A vector of bindings of key => step restriction key.

  This outputs the extra defs as specs with the specified restriction.

  Restriction keys are as follows:

  :no-state'   - [state action]
  :only-states - [state state']
  :only-action - [action]

  For example:

  (stepdef ::step
    {:extra-defs [::state-action :no-state'
                  ::states       :only-states
                  ::action       :only-action]}
    (:in-between/state
      ::drink (:in-between/state :empty/state)
      ::fill  (:in-between/state :full/state))
    (:empty/state
      ::fill (:in-between/state))
    (:full/state
      ::drink (:in-between/state)))

  will define ::step, ::state-action, ::states, and ::action specs.

  :data-def - A symbol that will define a map representation of forms.

  For example:

  (stepdef ::step
    {:data-def step-data}
    (:in-between/state
      ::drink (:in-between/state :empty/state)
      ::fill  (:in-between/state :full/state))
    (:empty/state
      ::fill (:in-between/state))
    (:full/state
      ::drink (:in-between/state)))

  will define both the ::step spec and

  (def step-data
    {:in-between/state
     {::drink #{:in-between/state :empty/state}
      ::fill #{:in-between/state :full/state}}
     :empty/state
     {::fill #{:in-between/state}}
     :full/state
     {::drink #{:in-between/state}}})
  "
  [k & forms]
  (let [[opts forms] (if (map? (first forms))
                       [(first forms) (rest forms)]
                       [nil forms])
        {:keys [extra-defs data-def]} opts
        bindings (concat [k :step] extra-defs)]
    `(do
       ~(when data-def
          `(def ~data-def ~(mapify-forms forms)))
       ~@(map (fn [[k restrict]]
                `(s/def ~k ~(step* forms :restrict restrict)))
              (partition 2 bindings)))))
