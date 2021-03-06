(ns flexmark-clj.core
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [clojure.string :as str]
   [flexmark-clj.marccup :as md])
  (:import
   (com.vladsch.flexmark.ext.admonition AdmonitionExtension)
   (com.vladsch.flexmark.html HtmlRenderer)
   (com.vladsch.flexmark.parser Parser)
   (com.vladsch.flexmark.util.ast Node)
   (com.vladsch.flexmark.util.data MutableDataSet)
   (java.util Arrays)))

(defn ^:private update-besu-methods
  "Pulls latest Besu API methods' documentation from Github."
  []
  (with-open [in  (io/input-stream "https://raw.githubusercontent.com/hyperledger/besu-docs/master/docs/Reference/API-Methods.md")
              out (io/output-stream "./resources/API-Methods.md")]
    (io/copy in out)))

(defn ^:private siblings
  [node]
  (if (nil? node)
    []
    (lazy-seq (cons node (siblings (.getNext node))))))

(defn ^:private children
  [parent]
  (siblings (.getFirstChild parent)))

(defprotocol Visitor
  (visit [this & {visit-fn :visit-fn}]))

(extend-protocol Visitor
  Node
  (visit [this & {visit-fn :visit-fn}]
    (visit-fn this (for [child (children this)]
                     (visit child :visit-fn visit-fn)))))

(defn markdown->tree
  "Parses a Markdown string into a tree of Flexmark nodes."
  [markdown]
  (let [extensions (Arrays/asList (into-array [(AdmonitionExtension/create)]))
        options    (doto (MutableDataSet.) (.set Parser/EXTENSIONS extensions))
        parser     (.build (Parser/builder options))]
    (.parse parser markdown)))

(defn tree->marccup
  "Walks the tree of Flexmark nodes, returns a Hiccup-esque Clojure array."
  [parsed]
  (visit parsed :visit-fn #(md/render %1 %2 {:unwrap-text true})))

(defn tree->html
  "Takes in a tree of Flexmark nodes, returns the rendered HTML in a string."
  [parsed]
  (let [renderer (.build (HtmlRenderer/builder))]
    (.render renderer parsed)))

(comment (with-open [reader (io/reader "./resources/API-Methods.md")
                     writer (io/writer "./resources/API-Methods.html")
                     edn    (io/writer "./resources/API-Methods.edn")]
           (let [markdown (->> reader line-seq (str/join "\n"))
                 tree     (markdown->tree markdown)
                 marccup  (tree->marccup tree)
                 html     (tree->html tree)]
             (pp/pprint marccup edn)
             (.write writer html))))
