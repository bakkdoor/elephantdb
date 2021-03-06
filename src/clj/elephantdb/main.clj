(ns elephantdb.main
  (:import [org.apache.thrift.server THsHaServer THsHaServer$Options])
  (:import [org.apache.thrift.protocol TBinaryProtocol TBinaryProtocol$Factory])
  (:import [org.apache.thrift TException])
  (:import [org.apache.log4j PropertyConfigurator])
  (:import [elephantdb.generated ElephantDB ElephantDB$Processor])
  (:import [org.apache.thrift.transport TNonblockingServerTransport TNonblockingServerSocket])
  (:require [elephantdb [service :as service]])
  (:use [elephantdb config log hadoop])
  (:gen-class))

(defn launch-server! [global-config local-config token]
  (let
      [options (THsHaServer$Options.)
       _ (set! (. options maxWorkerThreads) 64)
       service-handler (service/service-handler global-config local-config token)
       server (THsHaServer.
               (ElephantDB$Processor. service-handler)
               (TNonblockingServerSocket. (:port global-config))
               (TBinaryProtocol$Factory.) options)]
    (.addShutdownHook (Runtime/getRuntime) (Thread. (fn [] (.shutdown service-handler) (.stop server))))
    (log-message "Starting ElephantDB server...")
    (.serve server)))

;; the token is stored locally. If the token changes and there's newer data on the server, elephantdb will load it. Otherwise,
;; elephantdb just uses whatever is local
(defn -main [#^String global-config-hdfs-path #^String local-config-path #^String token]
  (PropertyConfigurator/configure "log4j/log4j.properties")
  (let [lfs (local-filesystem)
        local-config (merge DEFAULT-LOCAL-CONFIG
                            (read-clj-config lfs local-config-path))
        global-config (read-global-config global-config-hdfs-path local-config token)]
    (launch-server! global-config local-config token)))
