(defproject zmaxx_etl "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 
		[ring/ring-json "0.2.0"]
              [ring.middleware.jsonp "0.1.6"]
              [org.clojure/tools.logging "1.2.4"]
              ;;[org.slf4j/slf4j-log4j2 "2.17.2"]
              [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                           javax.jms/jms
                                                         com.sun.jmdk/jmxtools
                                                         com.sun.jmx/jmxri]]
                 [commons-net/commons-net "2.2"]
                 [commons-logging "1.1.1"]
                 [org.clojure/java.jdbc "0.3.5"]
                 [clj-http "3.12.3"]
                 [mysql/mysql-connector-java "5.1.25"]
                 [com.mchange/c3p0 "0.9.2.1"]
                 [dk.ative/docjure "1.14.0"]
                 [clj-time "0.15.2"]
                 [tea-time "1.0.1"]
                 [jarohen/chime "0.3.3"]
                 [org.clojure/data.json "2.4.0"]
                ]
  :repl-options {:init-ns zmaxx-etl.core}
  :profiles {:dev {:plugins [[cider/cider-nrepl "0.26.0"]]}
             :uberjar {:aot :all}}
  :main ^:skip-aot zmaxx-etl.nmint
  
)

