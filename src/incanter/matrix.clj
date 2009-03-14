;;; matrix.clj -- Matrix library for Clojure built on the CERN Colt Library

;; by David Edgar Liebke http://incanter.org
;; March 11, 2009

;; Copyright (c) David Edgar Liebke, 2009. All rights reserved.  The use
;; and distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution.  By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license.  You must not
;; remove this notice, or any other, from this software.

;; CHANGE LOG
;; March 11, 2009: First version



(ns incanter.matrix 
  (:import (incanter Matrix)
           (cern.colt.matrix DoubleMatrix2D 
                             DoubleFactory2D 
                             DoubleFactory1D)
           (cern.colt.matrix.linalg CholeskyDecomposition
                                    Algebra)
           (cern.colt.matrix.doublealgo Formatter)
           (cern.jet.math Functions)
           (cern.colt.function DoubleDoubleFunction DoubleFunction)))

(derive DoubleMatrix2D ::matrix)


(defn matrix 
  ([data]
   (cond 
     (coll? (first data)) 
      (Matrix. (into-array (map double-array data)))
     (number? (first data)) 
      (Matrix. (double-array data))))
  ([data ncol]
   (cond
     (coll? data)
      (Matrix. (double-array data) ncol)
     (number? data)
      (Matrix. data ncol))) ; data is the number of rows in this case
  ([init-val rows cols]
   (Matrix. rows cols init-val)))



(defn matrix? [m]
  (isa? (class m) ::matrix))


(defn nrow [mat]
  (cond 
    (matrix? mat) (.rows #^Matrix mat)
    (coll? mat) (count mat)))


(defn ncol [mat]
  (cond 
    (matrix? mat) (.columns #^Matrix mat)
    (coll? mat) 1 ))


(defn identity-matrix [dim]
   (Matrix. (.identity DoubleFactory2D/dense dim)))


(defn diag [m]
   (cond 
     (matrix? m)
      (into [] (seq (.toArray (.diagonal DoubleFactory2D/dense m))))
     (vector? m)
      (Matrix. (.diagonal DoubleFactory2D/dense (.make DoubleFactory1D/dense (double-array m))))))


(defn #^Matrix trans [mat]
  (cond 
    (matrix? mat)
      (.viewDice #^Matrix mat)
    (vector? mat)
      (.viewDice #^Matrix (matrix #^double-array mat))))


(defn sel [#^Matrix mat rows columns]
  (let [rws (if (number? rows) [rows] rows)
        cols (if (number? columns) [columns] columns)]
    (cond
      (and (number? rows) (number? columns))
        (.getQuick mat rows columns)
      (and (true? rws) (coll? cols))
        (.viewSelection mat (int-array (range (.rows mat))) (int-array cols))
      (and (coll? rws) (true? cols))
        (.viewSelection mat (int-array rws) (int-array (range (.columns mat))))
      (and (coll? rws) (coll? cols))
        (.viewSelection mat (int-array rws) (int-array cols))
      (and (true? rws) (true? cols))
        mat)))


(defn to-vect [#^Matrix mat]
  (into [] (cond
             (= (.columns mat) 1)
              (first (map #(into [] (seq %)) (seq (.toArray (.viewDice mat)))))
             (= (.rows mat) 1)
              (first (map #(into [] (seq %)) (seq (.toArray mat))))
             :else
              (map #(into [] (seq %)) (seq (.toArray mat))))))



(defn rbind [& args]
  (reduce
    (fn [A B] 
      (cond 
        (and (matrix? A) (matrix? B))
          (conj A B)
        (and (matrix? A) (coll? B))
          (conj A B)
        (and (coll? A) (matrix? B))
          (conj (matrix A (count A)) B)
        (and (coll? A) (coll? B))
          (conj (matrix A (count A)) (matrix B (count B)))
        :else
          (throw (Exception. "Incompatible types")))) 
      args))


(defn cbind [& args]
  (reduce 
    (fn [A B] (.viewDice (rbind (trans A) (trans B))))
    args))


;(defn inner-product [& args] (apply + (apply map * args))) 
;(inner-product [1 2 3] [4 5 6]) ; = 32


;;;; MATRIX OPERATIONS
(defn mmult [& args]
    (reduce (fn [A B]
              (let [a (if (matrix? A) A (matrix A))
                    b (if (matrix? B) B (matrix B))
                    result (Matrix. (.zMult #^Matrix a #^Matrix b nil))]
                (if (and (= (.rows result) 1) (= (.columns result) 1))
                  (.getQuick result 0 0)
                  result))) 
            args))


(defn #^Matrix chol [#^Matrix mat]
  (Matrix. (.viewDice (.getL (CholeskyDecomposition. mat))))) 


(defn #^Matrix copy [#^Matrix mat] (.copy mat))


(defmacro #^Matrix transform-with [A op fun]
  `(cond 
    (matrix? ~A)
      (.assign #^Matrix (.copy #^Matrix ~A) #^DoubleFunction (. Functions ~fun))
    (coll? ~A)
      (.assign #^Matrix (matrix ~A) #^DoubleFunction (. Functions ~fun))
    (number? ~A)
      (. Math ~op ~A)))
    

(defmacro combine-with [A B op fun]
  `(if (and (number? ~A) (number? ~B))
    (~op ~A ~B)
    (Matrix.
      #^DoubleMatrix2D
      (cond 
       (and (matrix? ~A) (matrix? ~B))
         (.assign #^Matrix (.copy #^Matrix ~A) #^Matrix ~B #^DoubleDoubleFunction (. Functions ~fun))
       (and (matrix? ~A) (number? ~B))
         (.assign #^Matrix (.copy #^Matrix ~A) #^DoubleDoubleFunction (. Functions (~fun ~B)))
       (and (number? ~A) (matrix? ~B))
         (.assign #^Matrix (matrix ~A (.rows ~B) (.columns ~B)) #^Matrix ~B #^DoubleDoubleFunction (. Functions ~fun))
       (and (coll? ~A) (matrix? ~B))
         (.assign #^Matrix (matrix ~A (.rows ~B) (.columns ~B)) #^Matrix (matrix ~B) #^DoubleDoubleFunction (. Functions ~fun))
       (and (matrix? ~A) (coll? ~B))
         (.assign #^Matrix (.copy ~A) #^Matrix (matrix ~B) #^DoubleDoubleFunction (. Functions ~fun))
       (and (coll? ~A) (coll? ~B))
         (.assign #^Matrix (matrix ~A) #^Matrix (matrix ~B) #^DoubleDoubleFunction (. Functions ~fun))
       (and (number? ~A) (coll? ~B))
         (.assign #^Matrix (matrix ~A (nrow ~B) (ncol ~B)) #^Matrix (matrix ~B) #^DoubleDoubleFunction (. Functions ~fun))
       (and (coll? ~A) (number? ~B))
         (.assign #^Matrix (matrix ~A) #^Matrix (matrix ~B (nrow ~A) (ncol ~A)) #^DoubleDoubleFunction (. Functions ~fun))))))
    


(defn plus [& args]
   (reduce (fn [A B] (combine-with A B + plus)) args))


(defn minus [& args]
   (reduce (fn [A B] (combine-with A B - minus)) args))


(defn mult [& args]
   (reduce (fn [A B] (combine-with A B * mult)) args))


(defn div [& args]
   (reduce (fn [A B] (combine-with A B / div)) args))


(defn pow [& args]
   (reduce (fn [A B] (combine-with A B Math/pow pow)) args))


(defn sqrt [A]
   (pow A 1/2))


(defn log [A]
   (transform-with A log log))


(defn exp [A]
   (transform-with A exp exp))


(defn solve [#^Matrix A & B]
  (if B
    (Matrix. (.solve (Algebra.) A (first B)))
    (Matrix. (.inverse (Algebra.) A))))




(defn to-1D-vect [mat]
  (cond 
    (and (coll? mat) (not (matrix? mat)))
      mat 
    (and (matrix? mat) (= (.columns mat) 1))
      (to-vect (.viewDice #^Matrix mat))
    (and (matrix? mat) (= (.rows mat) 1))
      (to-vect #^Matrix mat)
    (matrix? mat)
      (throw (Exception. "Argument must be a column or row matrix!"))))


(defn length [coll]
  (cond
    (number? coll) 
      1
    (coll? coll)
      (count coll)
    (matrix? coll)
      (* (.rows #^Matrix coll) (.columns #^Matrix coll))
    :else
      (throw (Exception. "Argument must be a collection or matrix!"))))
      


;; PRINT METHOD FOR COLT MATRICES

(defmethod print-method Matrix [o, #^java.io.Writer w]
  (let [formatter (Formatter. "%1.2f")]
    (do 
      (.setPrintShape formatter false)
      (.write w "[")
      (.write w (.toString formatter o))
      (.write w "]\n"))))


;(prefer-method print-method Matrix clojure.lang.ISeq)

