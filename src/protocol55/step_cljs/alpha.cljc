(ns protocol55.step-cljs.alpha
  (:require [cljs.spec.alpha :as s])
  #?(:cljs
     (:require-macros [protocol55.step-cljs.alpha :refer [stepdef]])))

#?(:clj
   (do

(defn step*
  [forms & {:keys [restrict]}]
  `(s/or ~@(mapcat
             (fn [[state & {:as action->states'}]]
               (let [state-qualifier (namespace state)]
                 `(~(keyword state-qualifier)
                  (s/or ~@(mapcat
                          (fn [[action states']]
                            `(~(-> action name keyword)
                              (s/or
                                ~@(mapcat
                                    (fn [state']
                                      (let [state'-qualifier (namespace state')]
                                        `(~(keyword state'-qualifier)
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

  Optionally, the second argument can be a map of options that excepts a single
  key, :extra-defs, which is a vector of bindings of key => step restrict key.
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
  "
  [k & forms]
  (let [[opts forms] (if (map? (first forms))
                       [(first forms) (rest forms)]
                       [nil forms])
        {:keys [extra-defs]} opts
        bindings (concat [k :step] extra-defs)]
    `(do
       ~@(map (fn [[k restrict]]
                `(s/def ~k ~(step* forms :restrict restrict)))
              (partition 2 bindings)))))

))
