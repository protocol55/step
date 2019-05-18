(ns protocol55.step.state.alpha
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen])
  #?(:cljs
     (:require-macros [protocol55.step.state.alpha :refer [cases]])))

(defn- ->spec [x]
  (cond
    (keyword? x)
    (s/get-spec x)

    (s/spec? x)
    x

    :else
    (throw (ex-info "Can't convert to spec!" {:x x}))))

(defprotocol CasesSpec
  (case-gen* [this k] "returns the generator for the case k"))

(defn case-gen [s k]
  (case-gen* (->spec s) k))

;; TODO: make into its own spec, without reliance on or-spec
;; This will allow support for (with-cases x :zero zero?) for generation and
;; conformance
(defn cases-spec-impl [or-spec pred-map gfn]
  (reify
    s/Specize
    (s/specize* [_]
      (s/specize* or-spec))
    (s/specize* [_ form]
      (s/specize* or-spec form))
    s/Spec
    (s/conform* [_ x]
      (s/conform* or-spec x))
    (s/unform* [_ y]
      (s/unform* or-spec y))
    (s/explain* [_ path via in x]
      (s/explain* or-spec path via in x))
    (s/gen* [_ overrides path rmap]
      (if gfn
        (gfn)
        (s/gen* or-spec overrides path rmap)))
    (s/with-gen* [_ gfn]
      (cases-spec-impl or-spec pred-map gfn))
    (describe* [_]
      (s/describe* or-spec))
    CasesSpec
    (case-gen* [_ k]
      (s/gen (k pred-map)))))

#?(:clj
   (do

(defmacro cases
  "Creates a spec similar to clojure.spec.alpha/or which is compatible with
  protocol55.step.state.alpha/produce.

  (state/cases :true true? :false false?)"
  [& key-pred-forms]
  (let [pred-map (apply hash-map key-pred-forms)]
    `(cases-spec-impl (s/or ~@key-pred-forms) ~pred-map nil)))

))

(defn- named-key [n m]
  (->> (keys m) (filter #(= (name %) n)) first))

(defn- produce* [of of-un conformed]
  (every? (fn [[k [ctag _]]]
            (if-not (namespace k)
              (= ctag (get of-un (named-key (name k) of-un)))
              (= ctag (k of))))
          conformed))

(defn- case-pairs [m & {:keys [un?]}]
  (mapcat (fn [[k v]]
            [(if un? (keyword (name k)) k) (case-gen k v)])
          (vec m)))

(defn produce
  "Creates and returns a map validating spec for specific cases of its keys. :of
  and :of-un are both maps of namespace-qualified keywords to case keywords.

  (state/produce ::state :of {::v :empty})"
  [spec & {:keys [of of-un]}]
  (s/spec (s/and spec #(produce* of of-un %))
          :gen (fn []
                 (->> (concat (case-pairs of)
                              (case-pairs of-un :un? true))
                      (apply gen/hash-map)))))

(comment
  (s/def ::m (cases :idle #{1} :busy #{0}))
  (s/def ::q (cases :empty #{0} :some (s/int-in 1 1e9)))
  (s/def ::ss-state (s/keys :req [::m ::q]))

  (s/def ::ss-empty-state empty?)
  (s/def ::ss-idle-state (produce ::ss-state :of {::m :idle ::q :some}))
  (s/def ::ss-idle-buffer-empty-state (produce ::ss-state :of {::m :idle ::q :empty}))
  (s/def ::ss-busy-state (produce ::ss-state :of {::m :busy ::q :some}))
  (s/def ::ss-busy-buffer-empty-state (produce ::ss-state :of {::m :busy ::q :empty}))

  (s/def ::initialize #{:initialize})
  (s/def ::arrive #{:arrive})
  (s/def ::load #{:load})
  (s/def ::unload #{:unload})

  (require '[protocol55.step.alpha :refer [stepdef]] :reload-all)

  (stepdef ::ss-step
    (::ss-empty-state
      ::initialize (::ss-idle-state))
    (::ss-idle-state
      ::arrive (::ss-idle-state)
      ::load (::ss-busy-state ::ss-busy-buffer-empty-state))
    (::ss-idle-buffer-empty-state
      ::arrive (::ss-idle-state))
    (::ss-busy-state
      ::arrive (::ss-busy-state)
      ::unload (::ss-busy-state ::ss-idle-state ::ss-idle-buffer-empty-state))
    (::ss-busy-buffer-empty-state
      ::arrive (::ss-busy-state)
      ::unload (::ss-idle-buffer-empty-state)))

  (s/conform ::ss-step (gen/generate (s/gen ::ss-step)))
  (s/valid? ::ss-step [{::m 0 ::q 0} :unload {::m 1 ::q 0}])

  )
