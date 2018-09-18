# protocol55.step.alpha

`step` is a library that lets you spec state-transition steps.

## Example

See the TodoMVC example from [Re-frame](https://github.com/Day8/re-frame/)
spec'd with `step`: https://github.com/colinkahn/todomvc-step

## Install

### deps.edn

`org.clojars.protocol55/step {:mvn/version "0.2.0"}`

### Leinigen

`[org.clojars.protocol55/step "0.2.0"]`

## Namespaces

Namespaces are split between Clojure and ClojureScript:

- Clojure: `protocol55.step.alpha`, `protocol55.step.alpha.specs`
- ClojureScript: `protocol55.step-cljs.alpha`, `protocol55.step-cljs.alpha.specs`

## Walkthrough

A step is defined as a tuple of:

```
[state action state']
```

We use `protocol55.step.alpha/stepdef` to define a step spec.

We'll begin by defining the state and action specs for our system:

```clojure
(s/def ::drink (s/tuple #{:drink}))
(s/def ::fill (s/tuple #{:fill}))

(s/def :in-between/v (s/int-in 1 10))
(s/def :in-between/state (s/keys :req-un [:in-between/v]))

(s/def :empty/v #{0})
(s/def :empty/state (s/keys :req-un [:empty/v]))

(s/def :full/v #{10})
(s/def :full/state (s/keys :req-un [:full/v]))
```

In the example above the system defines a state modeling a cup of water with a
single key `:v` (volume) whose value can be 0-10.

We then have two actions - `::drink` and `::fill`, which are
[Re-frame](https://github.com/Day8/re-frame/) style
tuples containing a keyword as the intent of the action.

Next we define the state variation specs. It's important to note that we are
using the same names with different qualifiers here. Because of limitations of
the `keys` spec this will only work `:opt-un` and `:req-un`.

With those in place we'll define our step spec:

```clojure
(stepdef ::step
 (:in-between/state
  ::drink (:in-between/state :empty/state)
  ::fill  (:in-between/state :full/state))
 (:empty/state
  ::fill (:in-between/state))
 (:full/state
  ::drink (:in-between/state)))
```

Each form within `stepdef` is defined as:

```
(state & (action (& state')))
```

This specifies the starting state of the step and then one or more
action-to-next-states pairs.

The ability to define multiple next-states (state') for an action lets us model
more complex systems than a normal state machine `state -> action -> state'`
tuple would.

Conformance for steps looks like the following:

```clojure
(s/conform ::step [{:v 1} [:drink] {:v 0}])
;; => [:in-between [:drink [:empty [{:v 1} [:drink] {:v 0}]]]]
```

where:

```clojure
[state-qualifier [action-name [state'-qualifier step-tuple]]]
```

Exercising gives us results conforming to our step spec:

```clojure
(s/exercise ::step)

([[{:v 0} [:fill] {:v 2}] [:empty [:fill [:in-between [{:v 0} [:fill] {:v 2}]]]]]
 [[{:v 1} [:drink] {:v 2}] [:in-between [:drink [:in-between [{:v 1} [:drink] {:v 2}]]]]]
 [[{:v 10} [:drink] {:v 1}] [:full [:drink [:in-between [{:v 10} [:drink] {:v 1}]]]]]
 [[{:v 10} [:drink] {:v 2}] [:full [:drink [:in-between [{:v 10} [:drink] {:v 2}]]]]]
 [[{:v 0} [:fill] {:v 3}] [:empty [:fill [:in-between [{:v 0} [:fill] {:v 3}]]]]]
 [[{:v 10} [:drink] {:v 1}] [:full [:drink [:in-between [{:v 10} [:drink] {:v 1}]]]]]
 [[{:v 10} [:drink] {:v 5}] [:full [:drink [:in-between [{:v 10} [:drink] {:v 5}]]]]]
 [[{:v 4} [:drink] {:v 0}] [:in-between [:drink [:empty [{:v 4} [:drink] {:v 0}]]]]]
 [[{:v 10} [:drink] {:v 8}] [:full [:drink [:in-between [{:v 10} [:drink] {:v 8}]]]]]
 [[{:v 8} [:fill] {:v 10}] [:in-between [:fill [:full [{:v 8} [:fill] {:v 10}]]]]])
```

### Options

`stepdef` accepts an optional map of options as its second argument. It allows
two keys: `:extra-defs` and `:data-def`.

#### Extra defs

`:extra-defs` can be used to create multiple specs with additional restrictions
on the kinds of steps produced.

```clojure
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
```

Restriction keys are as follows:

- `:no-state'` - `[state action]`
- `:only-states` - `[state state']`
- `:only-action` - `[action]`

These can be useful for specing functions that take a partial bit of a step,
such as a `next-state` function that takes a state and action and produces a
next state.

We can combine the actions and states into specs and use those to check such a
function.

```clojure
(s/def ::action (s/or :drink ::drink :fill ::fill))

(s/def ::state (s/or :in-between :in-between/state
                     :empty :empty/state
                     :full :full/state))

(defn next-state [state action]
  (case (first action)
    :drink (update state :v dec)
    :fill (update state :v inc)))

(s/fdef next-state
  :args ::state-action
  :ret ::state)
```

We can also make use of the `:fn` of `fdef` to `unform` `ret` and `args` to
fully validate against our full `::step` spec:

```clojure
(s/fdef next-state
  :args ::state-action
  :fn (fn [{:keys [ret args]}]
    (let [step (conj (vec (s/unform ::state-action args) (s/unform ::state ret)))]
      (s/valid? ::step step)))
  :ret ::state)
```

### Data def

`:data-def` can be used to assign a data representation of the `forms` in
`stepdef` to a variable.

```clojure
(stepdef ::step
  {:data-def step-data}
  (:in-between/state
    ::drink (:in-between/state :empty/state)
    ::fill  (:in-between/state :full/state))
  (:empty/state
    ::fill (:in-between/state))
  (:full/state
    ::drink (:in-between/state)))
```

The above will define both the `::step` spec definition as well as

```clojure
(def step-data
  {:in-between/state
   {::drink #{:in-between/state :empty/state}
    ::fill #{:in-between/state :full/state}}
   :empty/state
   {::fill #{:in-between/state}}
   :full/state
   {::drink #{:in-between/state}}})
```

One use for this data is to generate state transition graphs. We'll use the
[fsmvis](https://github.com/jebberjeb/fsmviz) library.

Assume we have the `stepdef` above defined in a namespace
`protocol55.step.example`. We can define a file to generate our graph like so:

```clojure
(require '[fsmviz.core :as fsmviz])
(require '[protocol55.step.example :refer [step-data]])

(def transitions
  (mapcat
    (fn [[state m]]
      (mapcat
        (fn [[action states']]
          (map #(mapv keyword %&)
               (repeat (namespace state))
               (repeat (name action))
               (map namespace states')))
        m))
    step-data))

(fsmviz/generate-image transitions "example-graph")
```

This outputs the following svg<sup>*</sup>:

![test-graph](https://user-images.githubusercontent.com/1444385/45701123-3c04d800-bb23-11e8-885d-05c946aa80b2.png)

<sup>*</sup> This is not the _exact_ svg. `fsmviz` defines an initial state of
`nil` which produces an unconnected black dot when using our transitions. I've
edited it out in the image above.

## Usage

### `protocol55.step.alpha/stepdef`

```
[k & forms]
Macro
  Defines a spec that validates a step as-per the transitions
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
```
