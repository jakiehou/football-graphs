(ns reactive.graph
  (:require ["d3" :as d3]))

(def scale (-> d3 (.scaleOrdinal (-> d3 (.-schemeCategory10)))))
(def color (fn [d] (scale (-> d .-group))))
(def canvas (-> js/document (.getElementById "canvas")))
(def ctx (-> canvas (.getContext "2d")))
(def w2 (/ (.-height canvas) 2))
(def h2 (/ (.-width canvas) 2))
(def node-radius 35)
(def distance 180)
(def transform (-> d3 .-zoomIdentity))

(defn find-node
  [nodes x y radius]
  (let [rsq (* radius radius)
        nodes-length (-> nodes count dec)]
    (loop [i 0]
      (let [interate? (< i nodes-length)
            node (get nodes i)
            dx (- x (.-x node))
            dy (- y (.-y node))
            dist-sq (+ (* dx dx) (* dy dy))
            node-found? (< dist-sq rsq)]
        (if node-found?
          node
          (if interate? (-> i inc recur)))))))

(defn force-simulation
  [width height]
  (-> d3
      (.forceSimulation)
      (.force "center" (-> d3 (.forceCenter (/ width 2) (/ height 2))))
      (.force "change" (-> d3 (.forceManyBody)))
      (.force "link" (-> d3 (.forceLink) (.distance distance) (.id (fn [d] (-> d .-id)))))))

(def simulation (force-simulation (.-width canvas) (.-height canvas)))

(defn update-coords
  [node]
  (-> node .-x (set! (-> transform (.applyX (-> node .-x)))))
  (-> node .-y (set! (-> transform (.applyY (-> node .-y))))))

(defn drag-subject
  [nodes]
  (let [x (-> transform (.invertX (-> d3 .-event .-x)))
        y (-> transform (.invertY (-> d3 .-event .-y)))
        node (find-node nodes x y node-radius)]
    (if node
      (update-coords node))
    node))

(defn drag-started
  []
  (-> simulation (.alphaTarget 1) (.restart))
  (-> d3 .-event .-subject .-fx (set! (-> transform (.invertY (-> d3 .-event .-x)))))
  (-> d3 .-event .-subject .-fy (set! (-> transform (.invertX (-> d3 .-event .-y))))))

(defn dragged
  []
  (-> d3 .-event .-subject .-fx (set! (-> transform (.invertY (-> d3 .-event .-x)))))
  (-> d3 .-event .-subject .-fy (set! (-> transform (.invertX (-> d3 .-event .-y))))))

(defn dragended
  []
  (-> simulation (.alphaTarget 0))
  (-> d3 .-event .-subject .-fx (set! nil))
  (-> d3 .-event .-subject .-fy (set! nil)))

(defn get-distance
  [x1 y1 x2 y2]
  (js/Math.sqrt (+ (js/Math.pow (- x2 x1) 2) (js/Math.pow (- y2 y1) 2))))

(defn find-point
  [x1 y1 x2 y2 distance1 distance2]
  {:x (- x2 (/ (* distance2 (- x2 x1)) distance1))
   :y (- y2 (/ (* distance2 (- y2 y1)) distance1))})

(defn draw-edges
  [edge]
  (let [target-x (-> edge .-target .-x)
        target-y (-> edge .-target .-y)
        source-x (-> edge .-source .-x)
        source-y (-> edge .-source .-y)
        value (-> edge .-value)
        point-dis (get-distance source-x source-y target-x target-y)
        weight-point (find-point
                       source-x
                       source-y
                       target-x
                       target-y
                       point-dis
                       (- point-dis (+ node-radius 15)))]
    (doto ctx
      (.save)
      ((fn [v] (set! (.-globalAlpha v) 0.2)))
      (.beginPath)
      (.moveTo source-x source-y)
      (.lineTo target-x target-y)
      ; ((fn [v] (set! (.-lineWidth v) (js/Math.sqrt value))))
      ((fn [v] (set! (.-lineWidth v) value)))
      ((fn [v] (set! (.-strokeStyle v) "#fff")))
      (.stroke)
      (.restore)
      ((fn [v] (set! (.-font v) "bold 18px sans-serif")))
      ((fn [v] (set! (.-textBaseline v) "middle")))
      ((fn [v] (set! (.-fillStyle v) "red")))
      ((fn [v] (set! (.-textAlign v) "center")))
      (.fillText value (weight-point :x) (weight-point :y))
      )))

(defn draw-nodes
  [node]
  (doto ctx
    (.beginPath)
    (.moveTo (+ (-> node .-x) node-radius) (-> node .-y))
    (.arc (-> node .-x) (-> node .-y) node-radius 0 (* 2 js/Math.PI))
    ((fn [v] (set! (.-fillStyle v) (color node))))
    (.fill)
    ((fn [v] (set! (.-font v) "20px sans-serif")))
    ((fn [v] (set! (.-fillStyle v) "#fff")))
    ((fn [v] (set! (.-textAlign v) "center")))
    ((fn [v] (set! (.-textBaseline v) "middle")))
    (.fillText (-> node .-id) (-> node .-x) (-> node .-y))
    ((fn [v] (set! (.-strokeStyle v) "#fff")))
    ((fn [v] (set! (.-lineWidth v) "1.5")))
    (.stroke)))

(defn simulation-update
  [edges nodes]
  (doto ctx
    (.save)
    (.clearRect 0 0 (.-width canvas) (.-height canvas))
    (.translate (-> transform .-x) (-> transform .-y))
    (.scale (-> transform .-k) (-> transform .-k)))
  (doseq [e edges] (draw-edges e))
  (doseq [n nodes] (draw-nodes n))
  (-> ctx (.restore)))

(defn force-graph
  [data]
  (let [nodes (-> data .-nodes)
        edges (-> data .-links)]
    (-> d3
        (.select canvas)
        (.call (-> d3
                   (.drag)
                   (.container canvas)
                   (.subject (fn [] (drag-subject nodes)))
                   (.on "start" drag-started)
                   (.on "drag" dragged)
                   (.on "end" dragended))))

    (-> simulation
        (.nodes nodes)
        (.on "tick" (fn [] (simulation-update edges nodes))))

    (-> simulation
        (.force "link")
        (.links edges))))

(def get-data
  (-> d3
      (.json "https://gist.githubusercontent.com/mbostock/4062045/raw/5916d145c8c048a6e3086915a6be464467391c62/miserables.json")))

; TODO: estabelecer posicionamento inicial dos nodes
; https://bl.ocks.org/mbostock/3750558
(def mock-data
  {
   :nodes [
           {:id "a" :group 1}
           {:id "b" :group 2}
           {:id "c" :group 3}
           ]
   :links [
           {:source "a" :target "b" :value 20}
           {:source "a" :target "c" :value 10}
           {:source "b" :target "a" :value 5}
           {:source "c" :target "b" :value 6}
           {:source "c" :target "a" :value 60}
           {:source "b" :target "c" :value 1}
           ]
   })

; https://observablehq.com/d/42f72efad452c2f0
; (defn init-graph [] (-> get-data (.then force-graph)))
(defn init-graph [] (-> mock-data clj->js force-graph))
