(ns zmaxx-etl.nmint
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



(defn is_lock? [file_name]
  (.exists (file file_name))
  )


(defn  unlock_ [file_name]
  (if (is_lock? file_name)
    (delete-file file_name))
  )















(defn action_policy []
  (info "process  action_policy[41]_______________________________")

  (let [baseDir (clojure.java.io/file (:order_path comm_/config_mint))
        files (file-seq baseDir)]
        (info "info--1-")
        ;; (info files)

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
          (if (> (count (vec sorted_files)) 0)
            ;;if-2--
            (let [first_f (first (vec sorted_files))]

              ;;判断是否锁了,有锁退出，没锁继续
              (let [lock_path   (clojure.string/join  "/" (drop-last (clojure.string/split (.getPath   first_f )   #"/")))
                    lock_file   (str  lock_path  "/policy.lock"  )]
                (if (is_lock? lock_file)
                  ;;if---
                  (do
                    (info (str  "file is lock!!=>" lock_file ))
                    (info "exit policy process")
                    )
                  ;;else-[core]--
                  (do
                    (info (str "lock file " lock_file ))
                    (lock_  lock_file)
                    (info (str "--->process file :" (.getPath first_f)))
                    (let [resul_is_ok (get (policy/action (.getPath   first_f )) :result)]
                      (info (str "action process:" resul_is_ok))
                      (if resul_is_ok
                        (do
                          (info (str  "rename done_policy:" (str (.getPath first_f) ".done_policy")))                          (info resul_is_ok)

                          (re-name first_f (str (.getPath first_f) ".done_policy")))
                        )
                      )
                    (info (str "unlock file " lock_file ))
                    (unlock_ lock_file)
                    )
                  )
                )
              )
            ;;else-2--
            )
          )
        )
  ;;判断锁文件是否存在，如果存在退出
   ;;不存在就创建一个
   ;;运行完删除锁文件
  (info  "---------------end policy")
  ;;(System/exit 0)
  )










(defn action_send []
  (info "process  action_send[41]_______________________________")
  (let [baseDir (clojure.java.io/file (:order_path comm_/config_mint))
        files (file-seq baseDir)]
        (info "begin process send-" )
       ;; (info files)

        (let [raw_files (remove (fn [x] (if (and (clojure.string/ends-with?  (.getPath x) ".json") )
                                          x))
                                files)
              new_files (filter (fn [x] (if (and (clojure.string/ends-with?  (.getPath x) ".done_policy")  (not (.isDirectory x)))
                                          x))
                                raw_files)
              sorted_files (sort-by (fn  [x]  (.lastModified x)) new_files)]
          (info "[send]info..1.1.")
          ;;(info (count sorted_files))
          ;;(info  sorted_files)
          ;;(info  (type  sorted_files))



          (if (>  (count (vec sorted_files)) 0)
            (let [first_f (first (vec sorted_files))]

              ;;判断是否锁了,有锁退出，没锁继续
              (let [lock_path   (clojure.string/join  "/" (drop-last (clojure.string/split (.getPath   first_f )   #"/")))
                    lock_file   (str  lock_path  "/send.lock"  )]
                (if (is_lock? lock_file)
                  ;;if----
                  (do
                    (info (str  "file is lock!!=>" lock_file ))
                    (info "exit send process")
                    )
                  ;;else----
                  (do (info (str "process file :" (.getPath first_f)))
                      (info (str "lock file " lock_file ))
                      (lock_  lock_file)
                      ;;core
                      (let [send_result (send/action (.getPath first_f))]
                         (if  (= "ok"  send_result)
                           ;;if
                           ;;昨晚后文件名修改为.done
                           (re-name first_f (str (.getPath first_f) ".done_send"))
                           ;;else
                           ""
                           )
                         )
                      (info (str "unlock file " lock_file ))
                      (unlock_ lock_file)
                      )
                  )
                )
              )
            )
          )
        )
  ;;不存在就创建一个
  ;;运行完删除锁文件

  (info  "---------------end send")
  ;;(System/exit 0)
  )









(defn -main [& args]
  (dotimes [i 50000] (println i)
           (do
             (action_policy)
             (info "sleep in   after policy begin ")
             (Thread/sleep (comm_/config_mint :sleep_action))
             (info "sleep in   after policy end")
             (action_send)
             (info "sleep in   after send begin ")
             (Thread/sleep (comm_/config_mint :sleep_action))
             (info "sleep in   after send end")
             )

           )
 )
