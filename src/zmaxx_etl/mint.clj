(ns zmaxx-etl.mint
  (:gen-class)

  (:require
   [zmaxx-etl.common :as comm_]
   [clojure.data.json :as json]
   [zmaxx-etl.send :as send]
   [zmaxx-etl.policy :as policy]
            )
   (:use
    [clojure.tools.logging :only (info error)]
    [clojure.java.io]
   )

  )

(use '[clojure.java.shell :only [sh]])




(require '[tea-time.core :as tt])

#_(defn nmain [& args]
    (println "input parameters
            args[0]: oper type ('mint_nft' or 'transaction_nft')
            if args[0] = 'mint_nft',then you should input else parameters
               args[1]: base_path
               args[2]: sub_path
               args[3]: nft_name
               args[4]: description
               args[5]: name
               args[6]: ipfs
               args[7]: mint wallet address
               args[8]: nft_amount
            else if args[0] = 'transaction_nft',then you should input else parameters
               args[1]: base_path
               args[2]: sub_path
               args[3]: from_address
               args[4]: to_address
               args[5]: policID
               args[6]: tokenname
               args[7]: send_token_amount

   ")
  (case (nth args 0)
    "mint_nft"  (policy/action_main    (nth args 1)
                                       (nth args 2)
                                       (nth args 3)
                                       (nth args 4)
                                       (nth args 5)
                                       (nth args 6)
                                       (nth args 7)
                                       (nth args 8)
                                       )
        "transaction_nft" (send/action_main (nth args 1)
                                            (nth args 2)
                                            (nth args 3)
                                            (nth args 4)
                                            (nth args 5)
                                            (nth args 6)
                                            (nth args 7)
                                            )
        "please inout param in ('mint_nft' or 'transaction_nft')"
        )
  )


(defn re-name
  "Rename a file"
  [old-path new-path]
  (.renameTo (file old-path) (file new-path)))





(defn wwwmain [& args]
  (let [baseDir (clojure.java.io/file (:order_path comm_/config_mint))
        files (file-seq baseDir)]
    (info "info--1-")
    (let [raw_files (remove (fn [x] (if (and (clojure.string/ends-with?  (.getPath x) "metadata.json") )
                       x))
                            files)
          new_files (filter (fn [x] (if (and (clojure.string/ends-with?  (.getPath x) ".json")  (not (.isDirectory x)))
                                      x))
                            raw_files)
          sorted_files (sort-by (fn  [x]  (.lastModified x)) new_files)]
      (info "info..1.1.")

      (let [process_json_files (.getPath sorted_files)]
        (map  (fn [x]
                (info (str "process file :" x))

                  )
                process_json_files)
          )
      #_(doseq [i (range 5)]
        (Thread/sleep 1000)

        )
      )
    )

  (info "info--2-")
  )







(defn lock_ [file_name]
  (with-open [wrtr (writer file_name)]
    (.write wrtr "lock "))
  )


(defn  unlock_ [file_name]
  (delete-file file_name)
  )


(defn is_lock? [file_name]
  (.exists (file file_name))
  )













(defn action []
  (info "process  action_______________________________")
  ;;判断锁文件是否存在，如果存在退出
  (if (is_lock? (get comm_/config_mint :lock_policy_file))
      (do
        (info "ploicy  lock ,exit ")
        (System/exit 0)
        )


    (do
      (lock_ (get comm_/config_mint :lock_policy_file))
      (let [baseDir (clojure.java.io/file (:order_path comm_/config_mint))
        files (file-seq baseDir)]
        (info "info--1-")
        (info files)

    (let [raw_files (remove (fn [x] (if (and (clojure.string/ends-with?  (.getPath x) "metadata.json") )
                       x))
                            files)
          new_files (filter (fn [x] (if (and (clojure.string/ends-with?  (.getPath x) ".json")  (not (.isDirectory x)))
                                      x))
                            raw_files)
          sorted_files (sort-by (fn  [x]  (.lastModified x)) new_files)]
     ;; (info  raw_files)
     ;; (info  new_files)
      (info "[policy]info..1.1.")
     ;; (info (count sorted_files))
     ;; (info  sorted_files)
     ;; (info  (type  sorted_files))
      (dorun
       (map  (fn [x]
               (do (info (str "process file :" (.getPath x)))
                   (info "process----[policy][begin]---")
                   (policy/action (.getPath x))
                   (info "process----[policy][end]---")
                   (re-name x (str (.getPath x) ".policy_done"))

                   ;;sleep
                   (info "sleep      policy begin")
                   (Thread/sleep (comm_/config_mint :policy_everyfile_sleep))
                   (info "sleep      policy end")
                   (info "process----[send][begin]---")
                   (let [send_result (send/action (.getPath  (str x ".policy_done")))]
                     (if  (= "ok"  send_result)
                       ;;if
                        ;;昨晚后文件名修改为.done
                       (re-name x (str (.getPath x) ".send_done"))
                       ;;else
                       ""
                       )
                     )
                   (info "process----[send][end]---")
                   (info "sleep      send begin")
                   (Thread/sleep (comm_/config_mint :send_everyfile_sleep))
                   (info "sleep      send end")

                   )
               )
             (vec sorted_files)))

      )
    )

      (unlock_ (get comm_/config_mint :lock_policy_file))
      )
    )
  ;;不存在就创建一个
  ;;运行完删除锁文件
   (info  "---------------end  ")
  (System/exit 0)
  )



#_(defn -main [& args]
  (action)
  #_(case (nth args 0)
    "send"  (action_send)
    "policy"    (action_policy)
    "please inout param in ('mint_nft' or 'transaction_nft')"
    )
  (info "over")
  (System/exit 0)
  )
