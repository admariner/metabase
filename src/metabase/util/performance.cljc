(ns metabase.util.performance
  "Functions and utilities for faster processing. This namespace is compatible with both Clojure and ClojureScript.
  However, some functions are either not only available in CLJS, or offer passthrough non-improved functions."
  (:refer-clojure :exclude [reduce mapv run! some every? concat select-keys])
  #?@(:clj [(:import (clojure.lang LazilyPersistentVector RT)
                     java.util.Iterator)]
      :default ()))

#?(:clj (set! *warn-on-reflection* true))

#?(:clj
   (defn reduce
     "Like `clojure.core/reduce`, but uses iterators under the hood to walk the collections and can iterate several
  collections at once. The function `f` accepts the number of arguments that is the number of iterated collections +
  1 (accumulator)."
     ([f init coll1]
      (if (nil? coll1)
        init
        (let [it1 (.iterator ^Iterable coll1)]
          (loop [res init]
            (if (.hasNext it1)
              (let [res (f res (.next it1))]
                (if (reduced? res)
                  @res
                  (recur res)))
              res)))))
     ([f init coll1 coll2]
      (if (or (nil? coll1) (nil? coll2))
        init
        (let [it1 (.iterator ^Iterable coll1)
              it2 (.iterator ^Iterable coll2)]
          (loop [res init]
            (if (and (.hasNext it1) (.hasNext it2))
              (let [res (f res (.next it1) (.next it2))]
                (if (reduced? res)
                  @res
                  (recur res)))
              res)))))
     ([f init coll1 coll2 coll3]
      (if (or (nil? coll1) (nil? coll2) (nil? coll3))
        init
        (let [it1 (.iterator ^Iterable coll1)
              it2 (.iterator ^Iterable coll2)
              it3 (.iterator ^Iterable coll3)]
          (loop [res init]
            (if (and (.hasNext it1) (.hasNext it2) (.hasNext it3))
              (let [res (f res (.next it1) (.next it2) (.next it3))]
                (if (reduced? res)
                  @res
                  (recur res)))
              res)))))
     ([f init coll1 coll2 coll3 coll4]
      (if (or (nil? coll1) (nil? coll2) (nil? coll3) (nil? coll4))
        init
        (let [it1 (.iterator ^Iterable coll1)
              it2 (.iterator ^Iterable coll2)
              it3 (.iterator ^Iterable coll3)
              it4 (.iterator ^Iterable coll4)]
          (loop [res init]
            (if (and (.hasNext it1) (.hasNext it2) (.hasNext it3) (.hasNext it4))
              (let [res (f res (.next it1) (.next it2) (.next it3) (.next it4))]
                (if (reduced? res)
                  @res
                  (recur res)))
              res))))))

   :cljs
   (defn reduce
     "Passthrough fallback to `clojure.core/reduce`."
     [f init coll]
     (clojure.core/reduce f init coll)))

;; Special case for mapv. If the iterated collection has size <=32, it is more efficient to use object array as
;; accumulator instead of transients, and then build a vector from it.

#?(:clj
   (definterface ISmallTransient
     (conj [x])
     (persistent [])))

#?(:clj
   (deftype SmallTransientImpl [^objects arr, ^:unsynchronized-mutable ^long cnt]
     ISmallTransient
     (conj [this x]
       (RT/aset arr (unchecked-int cnt) x)
       (set! cnt (unchecked-inc cnt))
       this)

     (persistent [_]
       (LazilyPersistentVector/createOwning arr)))

   :cljs
   (deftype SmallTransientImpl [^:mutable arr, ^:mutable cnt, f]))

#?(:clj
   (defn- small-transient [n]
     (SmallTransientImpl. (object-array n) 0))

   :cljs
   ;; Storing `f` in the transient itself is a hack to reduce lambda generation because we only use small-transients
   ;; in CLJS for a single arity in `mapv`.
   (defn- small-transient [n f]
     (SmallTransientImpl. (object-array n) 0 f)))

#?(:clj
   (defn- small-conj!
     {:inline (fn [st x] `(.conj ~(with-meta st {:tag `ISmallTransient}) ~x))}
     [^ISmallTransient st x]
     (.conj st x))

   :cljs
   (defn- small-conj-with-f! [st x]
     (let [cnt (.-cnt st)]
       (do (aset (.-arr st) cnt ((.-f st) x))
           (set! (.-cnt st) (inc cnt))
           st))))

#?(:clj
   (defn- small-persistent! [^ISmallTransient st]
     (.persistent st))

   :cljs
   (defn- small-persistent! [st]
     (let [cnt (.-cnt st)
           arr (.-arr st)]
       (PersistentVector. nil cnt 5 (.-EMPTY-NODE PersistentVector) arr nil))))

#?(:clj
   (defn- smallest-count
     (^long [c1 c2] (min (count c1) (count c2)))
     (^long [c1 c2 c3] (min (count c1) (count c2) (count c3)))
     (^long [c1 c2 c3 c4] (min (count c1) (count c2) (count c3) (count c4)))))

#?(:clj
   (defn mapv
     "Like `clojure.core/mapv`, but iterates multiple collections more efficiently and uses Java iterators under the hood."
     ([f coll1]
      (let [n (count coll1)]
        (cond (= n 0) []
              (<= n 32) (small-persistent! (reduce #(small-conj! %1 (f %2)) (small-transient n) coll1))
              :else (persistent! (reduce #(conj! %1 (f %2)) (transient []) coll1)))))
     ([f coll1 coll2]
      (let [n (smallest-count coll1 coll2)]
        (cond (= n 0) []
              (<= n 32) (small-persistent! (reduce #(small-conj! %1 (f %2 %3)) (small-transient n) coll1 coll2))
              :else (persistent! (reduce #(conj! %1 (f %2 %3)) (transient []) coll1 coll2)))))
     ([f coll1 coll2 coll3]
      (let [n (smallest-count coll1 coll2 coll3)]
        (cond (= n 0) []
              (<= n 32) (small-persistent! (reduce #(small-conj! %1 (f %2 %3 %4)) (small-transient n) coll1 coll2 coll3))
              :else (persistent! (reduce #(conj! %1 (f %2 %3 %4)) (transient []) coll1 coll2 coll3)))))
     ([f coll1 coll2 coll3 coll4]
      (let [n (smallest-count coll1 coll2 coll3 coll4)]
        (cond (= n 0) []
              (<= n 32) (small-persistent! (reduce #(small-conj! %1 (f %2 %3 %4 %5)) (small-transient n) coll1 coll2 coll3 coll4))
              :else (persistent! (reduce #(conj! %1 (f %2 %3 %4 %5)) (transient []) coll1 coll2 coll3 coll4))))))

   :cljs
   (defn mapv
     "Like `clojure.core/mapv`, but is more efficient for small vectors."
     ([f coll1]
      (let [n (count coll1)]
        (cond (= n 0) []
              (<= n 32) (small-persistent! (reduce small-conj-with-f! (small-transient n f) coll1))
              :else (persistent! (reduce #(conj! %1 (f %2)) (transient []) coll1)))))))

(defn run!
  "Like `clojure.core/run!`, but iterates collections more efficiently and uses Java iterators under the hood."
  ([f coll1]
   (reduce (fn [_ x] (f x)) nil coll1)))

(defn juxt*
  "Like `clojure.core/juxt`, but accepts a list of functions instead of varargs. Uses more efficient mapping."
  [fns]
  (let [fns (vec fns)]
    (fn
      ([] (mapv #(%) fns))
      ([x] (mapv #(% x) fns))
      ([x y] (mapv #(% x y) fns))
      ([x y z] (mapv #(% x y z) fns))
      ([x y z & args] (mapv #(apply % x y z args) fns)))))

(defn some
  "Like `clojure.core/some` but uses our custom `reduce` which in turn uses iterators."
  [f coll]
  (unreduced (reduce #(when-let [match (f %2)] (reduced match)) nil coll)))

(defn every?
  "Like `clojure.core/every?` but uses our custom `reduce` which in turn uses iterators."
  [f coll]
  (unreduced (reduce #(if (f %2) true (reduced false)) true coll)))

(defn concat
  "Like `clojure.core/concat` but accumulates the result into a vector."
  ([a b]
   (into (vec a) b))
  ([a b c]
   (as-> (transient (vec a)) res
     (reduce conj! res b)
     (reduce conj! res c)
     (persistent! res)))
  ([a b c d]
   (as-> (transient (vec a)) res
     (reduce conj! res b)
     (reduce conj! res c)
     (reduce conj! res d)
     (persistent! res)))
  ([a b c d e]
   (as-> (transient (vec a)) res
     (reduce conj! res b)
     (reduce conj! res c)
     (reduce conj! res d)
     (reduce conj! res e)
     (persistent! res)))
  ([a b c d e f]
   (as-> (transient (vec a)) res
     (reduce conj! res b)
     (reduce conj! res c)
     (reduce conj! res d)
     (reduce conj! res e)
     (reduce conj! res f)
     (persistent! res)))
  ([a b c d e f & more]
   (as-> (transient (vec a)) res
     (reduce conj! res b)
     (reduce conj! res c)
     (reduce conj! res d)
     (reduce conj! res e)
     (reduce conj! res f)
     (reduce (fn [res l] (reduce conj! res l)) res more)
     (persistent! res))))

#?(:clj
   (defn transpose
     "Like `(apply mapv vector coll-of-colls)`, but more efficient."
     [coll-of-colls]
     (let [its (mapv #(.iterator ^Iterable %) coll-of-colls)]
       (mapv (fn [_] (mapv #(.next ^Iterator %) its))
             (first coll-of-colls)))))

;; clojure.walk reimplementation. Partially adapted from https://github.com/tonsky/clojure-plus.

#?(:clj
   (do
     (defn- editable? [coll]
       (instance? clojure.lang.IEditableCollection coll))

     (defn- transient? [coll]
       (instance? clojure.lang.ITransientCollection coll))

     (defn- assoc+ [coll key value]
       (cond
         (transient? coll) (assoc! coll key value)
         (editable? coll)  (assoc! (transient coll) key value)
         :else             (assoc  coll key value)))

     (defn- dissoc+ [coll key]
       (cond
         (transient? coll) (dissoc! coll key)
         (editable? coll)  (dissoc! (transient coll) key)
         :else             (dissoc  coll key)))

     (defn- maybe-persistent! [coll]
       (cond-> coll
         (transient? coll) persistent!))

     (defn walk
       "Like `clojure.walk/walk`, but optimized for efficiency and has the following behavior differences:
  - Doesn't walk over map entries. When descending into a map, walks keys and values separately.
  - Uses transients and reduce where possible and tries to return the same input `form` if no changes were made."
       [inner outer form]
       (cond
         (map? form)
         (let [new-keys (volatile! (transient #{}))]
           (-> (reduce-kv (fn [m k v]
                            (let [k' (inner k)
                                  v' (inner v)]
                              (if (identical? k' k)
                                (if (identical? v' v)
                                  m
                                  (assoc+ m k' v'))
                                (do (vswap! new-keys conj! k')
                                    (if (contains? @new-keys k)
                                      (assoc+ m k' v')
                                      (-> m (dissoc+ k) (assoc+ k' v')))))))
                          form form)
               maybe-persistent!
               (with-meta (meta form))
               outer))

         (vector? form)
         (-> (reduce-kv (fn [v idx el]
                          (let [el' (inner el)]
                            (if (identical? el' el)
                              v
                              (assoc+ v idx el'))))
                        form form)
             maybe-persistent!
             (with-meta (meta form))
             outer)

         ;; Don't care much about optimizing seq and generic coll cases. When efficiency is required, use vectors.
         (seq? form) (outer (with-meta (seq (mapv inner form)) (meta form))) ;;
         (coll? form) (outer (with-meta (into (empty form) (map inner) form) (meta form)))
         :else (outer form)))

     (defn prewalk
       "Like `clojure.walk/prewalk`, but uses a more efficient `metabase.util.performance/walk` underneath."
       [f form]
       (walk (fn prewalker [form] (walk prewalker identity (f form))) identity (f form)))

     (defn postwalk
       "Like `clojure.walk/postwalk`, but uses a more efficient `metabase.util.performance/walk` underneath."
       [f form]
       (walk (fn postwalker [form] (walk postwalker f form)) f form))

     (defn keywordize-keys
       "Like `clojure.walk/keywordize-keys`, but uses a more efficient `metabase.util.performance/walk` underneath and
  preserves original metadata on the transformed maps."
       [m]
       (postwalk
        (fn [form]
          (if (map? form)
            (-> (reduce-kv (fn [m k v]
                             (if (string? k)
                               (-> m (dissoc+ k) (assoc+ (keyword k) v))
                               m))
                           form form)
                maybe-persistent!
                (with-meta (meta form)))
            form))
        m))))

(defn select-keys
  "Like `clojure.walk/select-keys`, but much more efficient."
  [m keyseq]
  (let [absent #?(:clj (Object.) :cljs #js{})]
    (persistent! (reduce (fn [acc k]
                           (let [v (get m k absent)]
                             (if (identical? v absent)
                               acc
                               (assoc! acc k v))))
                         (transient {}) keyseq))))
