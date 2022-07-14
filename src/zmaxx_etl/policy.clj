(ns zmaxx-etl.policy
  (:gen-class)
  (:use
   [clojure.tools.logging :only (info error)]
   [clojure.java.io :as io]

   )
   (:require
    [zmaxx-etl.common :as comm_]
    [clj-http.client :as client]

    )
   )
(require '[clojure.data.json :as json])


;;(require '[clojure.java.io :as io])


(use 'dk.ative.docjure.spreadsheet)
(use '[clojure.java.shell :only [sh]])

;;(require '[clojure.data.json :as json])


(defn foo
  "I don't do a whole lot."
  [x]
  (info x "Hello, World!"))

(def cardano_cli (comm_/config_mint :cardano_cli))


(def value_map
  {:invalid-hereafter (comm_/config_mint :policy_before_slot)
   ;;:wallet_address "/home/ubuntu/test_cardano/keys/payment1.addr"
   :paymentsky_file  (comm_/config_mint :paymentsky_file_) }
  )


(def policy_key ["policy.vkey" "policy.skey"])

(def command_init_key
  [cardano_cli "address" "key-gen" "--verification-key-file" " "  "--signing-key-file" " "]
  )

(def command_get_policy_keyhash
  [cardano_cli "address" "key-hash" "--payment-verification-key-file" " "]
  )
(def command_init_policyID
  [cardano_cli "transaction" "policyid" "--script-file" ""])


(def command_get_tx
  [cardano_cli "query" "utxo" "--testnet-magic" "1097911063" "--address"])

(def command_build_raw
  [cardano_cli "transaction" "build" "--testnet-magic" "1097911063" "--alonzo-era"])

(def command_build_sign
  [cardano_cli "transaction" "sign" "--signing-key-file" "" "--signing-key-file" "" "--testnet-magic 1097911063 --tx-body-file" "" " --out-file " ""])

(def command_submit
  [cardano_cli " transaction submit --tx-file " "" " --testnet-magic 1097911063"])




(def template_policy_script
  {:type "all"
   :scripts [
            {
             :type "before"
             :slot   (comm_/config_mint :policy_before_slot)
             }
            {
             :type "sig"
             :keyHash "replace_this"
             }
            ]
   }
  )


;;[1]生成policy key
;;[2]生成policy.script
;;[3]生成policy ID
(defn init_policy_key [base_path sub_path ]
  (let [policy_path           (clojure.string/join "/"   [base_path    sub_path])]
    (let  [vkey_file          (clojure.string/join "/"   [policy_path  (get policy_key 0)])
           skey_file          (clojure.string/join "/"   [policy_path  (get policy_key 1)])
           policy_script_file (clojure.string/join "/"   [policy_path  "policy.script"])
           policyID_file      (clojure.string/join "/"   [policy_path  "policyID"])]
      (do
        ;;step 1判断目录是否存在
        (if-not (.isDirectory (io/file policy_path))
          ;;如果不存在
          (.mkdir (io/file policy_path))
          )
        ;;生成key
        (apply sh
               (assoc command_init_key 4 vkey_file 6 skey_file)))
      ;;生成keyhash,并添加到policy.script
      (let [keyhash_str (clojure.string/replace
                         (:out
                          (apply sh
                                 (assoc command_get_policy_keyhash 4 vkey_file)))
                         "\n" "") ]
        ;;写入script文件
        (spit
         policy_script_file
         (json/write-str
          (assoc-in template_policy_script [:scripts 1 :keyHash] keyhash_str))
         )
        )
      ;;写入policyID文件
      (let [policyID_str (clojure.string/replace
                          (:out
                           (apply sh
                                  (assoc command_init_policyID 4 policy_script_file ))
                           )
                          "\n" ""
                          )]
        (spit
         policyID_file
         policyID_str
         )
        [policy_script_file policyID_str]
        )
      )
    )
  )

;;-------------meta data-------------
;;生成metadata.json 文件
(defn init_metadata [policyID nft_name description name  ipfs_uri metadata_file]
  (let [nft
        {:721
         {policyID
         {nft_name
               {
                :description description
                :name name
                :id 1
                :image (str "ipfs://" ipfs_uri)
               }
          }}}]
    (spit
     metadata_file
     (json/write-str nft))
    )
  )


;;--------------tx----------------------

(defn build_tx_out [address output tokenamount policyID tokenname txs]
  (let [tx_out_str (str address  "+" output  "+'"  tokenamount " " policyID "." tokenname)]
    ;;(log.info txs)
    ;;(log.info (map (fn [x] (str (nth x 5) " " (nth x 6)))  txs))
    (str tx_out_str  " + " (clojure.string/join " + "
                                                #_(map (fn [x] (str (nth x 5) " " (nth x 6)))
                                                       txs)
                                                (map
                                                 (fn [x]
                                                   (clojure.string/join " " (drop-last 2
                                                                                       (drop 5 x)))
                                                   )(remove (fn [x] (< (count x) 7)) txs))
                                                ) "'")
    )
  )


(defn build_tx_map [done_tx_in done_tx_out address tokenamount policyID tokenname policy_script_file metadata_file base_path sub_path]
  (reduce  conj done_tx_in  ["--tx-out"              done_tx_out
                                                    "--change-address"      address (str "--mint='" tokenamount  " " policyID  "." tokenname "'")
                                                    "--minting-script-file" policy_script_file
                                                    "--metadata-json-file"  metadata_file
                                                    "--invalid-hereafter"   (:invalid-hereafter value_map)
                                                    "--witness-override"    2
                                                    "--out-file"  (str base_path  "/" sub_path   "/matx.raw")])
  )




(defn check_raw [out_]
  ;;(log.info out_)
  (if (contains? out_ :out)
    (if (contains? out_ :err)
      (let [err_lines (clojure.string/split-lines (:err out_))]
        (if (> (count err_lines) 1)
          (let [line1 (nth err_lines 1)]
          ;;  (log.info (str "<--" line1  "-->"))
          ;;  (log.info (type line1) )
            (let [result_match (re-matches #"(?i)Minimum required UTxO: Lovelace \d+" line1)]
              (if (some? result_match)
                (Long/valueOf  (last (clojure.string/split line1  #" ")))
                nil
                )
              )
            )
          )
        )
      )
    ;;    (log.info "error !! has not :out")
    nil
    )
  )


(defn check_sh_result [out_]
  ;;(log.info out_)
  (if (contains? out_ :out)
    (if (clojure.string/includes? (get  out_ :out)  "success" )
      {:result true}
      {:result false}
      )
    {:result false}
    )
  )



(defn tx_build [token_name policyID policy_script_file metadata_file base_path address_path nft_amount sub_path]
  (let [tx_long_string  (:out (apply sh  (conj  command_get_tx  (slurp address_path))))
        txs          (map (fn [x] (remove clojure.string/blank?  (clojure.string/split x #" ")))
                      (drop 2 (clojure.string/split-lines  tx_long_string)) )
      list_txs     (map (fn [x]  (str (nth x 0) "#" (nth x 1))) txs)
      done_tx_in   (reduce (fn [x y] (conj x "--tx-in" y)) command_build_raw  list_txs)
      tx-out       "--tx-out"
      address      (slurp  address_path)
      output       1400000
      tokenamount  nft_amount
        tokenname    (:out (sh "bash" "-c"  (str  "echo -n '" token_name  "' | xxd -b -ps -c 80 | tr -d '\n'")))
        ada_amount (apply +   (map (fn [x] (Long/valueOf  (nth x 2))) txs))
        ]

      (let [done_tx_out  (build_tx_out address output tokenamount policyID tokenname txs)]
        (let [raw_tx_map (build_tx_map done_tx_in done_tx_out address tokenamount policyID tokenname policy_script_file metadata_file base_path sub_path)]
          ;;raw_tx_map
          (let [mini_fee (check_raw
                          (sh "bash" "-c" (clojure.string/join " " raw_tx_map)))]
            (if (some? mini_fee)
             ;; mini_fee
              (let [new_done_tx_out   (build_tx_out address mini_fee tokenamount policyID tokenname txs)
                    new_raw_tx_map    (build_tx_map done_tx_in new_done_tx_out address tokenamount policyID tokenname policy_script_file metadata_file base_path sub_path)
                    ]
                (info new_raw_tx_map)
                (sh "bash" "-c" (clojure.string/join " " new_raw_tx_map))
                )
              )
            )
          ;;(apply  sh  raw_tx_map)
          )
        )
    )
  )







(defn befor_tx [base_path sub_path nft_name description name  ipfs_uri]
  (let [items (init_policy_key base_path sub_path)
        metadata_file (str base_path  "/" sub_path   "/" "metadata.json")
        policyID (get items 0)
        policy_script_file (get items 1)
        ]
    (init_metadata policyID nft_name description name  ipfs_uri metadata_file)
    )
  )

(defn estimated_build_fee [out_string]
  (let [out_ (clojure.string/replace   out_string #"\r?\n" "")
        result_match (re-matches #"(?i)Estimated transaction fee: Lovelace \d+" out_)]
    (if (some? result_match)
      (Long/valueOf  (last (clojure.string/split out_  #" ")))
      (do
        (error   out_)
        (error   (re-matches #"(?i)Estimated transaction fee: Lovelace \d+" out_))
        (error   (some? result_match))
        (error " estimated_build_fee error .")
        nil)
      )
    )
  )




;;--------------sign tx----------------------
;;生成签名文件 matx.signed
(defn sign_tx_submit [base_path sub_path]
  (let [paymentskey_file (:paymentsky_file value_map)
        policyskey_file (str  base_path  "/" sub_path "/policy.skey")
        raw_file (str base_path  "/" sub_path   "/matx.raw")
        sign_file (str base_path  "/" sub_path  "/matx.signed")]
    ;;sign
    (let [result_sign (sh "bash" "-c" (clojure.string/join " "
                                                           (assoc command_build_sign 4 paymentskey_file 6 policyskey_file 8 raw_file 10 sign_file )))]
      (info  (str "-in:" "sign_tx_submit [1] " ))
      (info result_sign)
      (info  (str "-in:" "sign_tx_submit [2] " ))

      (let [submit_result (sh "bash" "-c" (clojure.string/join " "
                                                               (assoc command_submit 2 sign_file )))]

        (info submit_result)
        (info "sign_tx_submit  done!")
        submit_result
        )
      )
    )
  )




;;读取order json文件,
(defn read_json_order [json_file]
  ;;读取json
  ;; 获取sub_path, address为sub_path
  ;; call main
  ;;修改文件名后缀【.p】
  ;;

  )



(defn get_order_value [k json_file]
   (let [json_d (json/read-str (slurp json_file)
                               )]
      (case k
        "create_date" (get json_d "create_date")
        "nfts" (get json_d "nfts")
        "client_address" (get json_d "from_address")
        "default"
        )
     )
  )

(defn  get_first_nft [k json_file]
    (let [nfts (get_order_value "nfts" json_file)
          nft (first nfts)]
      (case k
        "count" (get nft "count")
        "name" (get nft "name")
        "describe" (get nft "describe")
        "nft_name" (get nft "nft_name")
        "ipfs" (get nft "ipfs")
        ""
        )
      )
  )

(defn is_policy_online? [policyID nft_name]
  (let [
        asset (str policyID nft_name)
        url_  (str "https://cardano-testnet.blockfrost.io/api/v0/assets/" asset)  ]

    (info url_)
    (let [result_ (try
                    (count
                     (get
                      (client/get url_
                                  {:headers {:project_id "testnetSYmyIVNExxxxxb"}})
                      :body
                      ))
                    (catch clojure.lang.ExceptionInfo e (error "catch e: " e)
                           0
                           )
                    )]
      (if (> result_ 0)
        true
        false
        )

      )
    )
  )


(defn getPrevious_pid []
  (clojure.string/split
   (slurp (comm_/config_mint :previous_policyID_file))
   #"/"
   )
  )

(defn action_and_check_privious_policyID_is_ok []
  (let [pid_and_name (getPrevious_pid)]
    (info pid_and_name)
    (is_policy_online?  (first  pid_and_name)  (second pid_and_name))
    )
  )







(defn action [json_file]
  (let  [client_address   (first (take-last 3  (clojure.string/split json_file   #"/")))
         order_date         (first (take-last 2  (clojure.string/split json_file   #"/")))
         sub_path         (str client_address  "/"  order_date)
         nft_name         (get_first_nft "nft_name" json_file)
         description      (get_first_nft "describe" json_file)
         name             (get_first_nft "name"     json_file)
         ipfs             (get_first_nft "ipfs"     json_file)
         nft_amount       (get_first_nft "count"    json_file)

         ;;file_one
         items (init_policy_key (comm_/config_mint :basePath) sub_path)

         metadata_file (str (comm_/config_mint :basePath)  "/" sub_path  "/" "metadata.json")
         policyID (get items 1)
         policy_script_file (get items 0)
         ]


    (do
        (init_metadata policyID nft_name description name  ipfs metadata_file)
        (info "init_metadata over! ")
        ;;(info policyID)

        (try
          (let [tx_build_result (tx_build  nft_name policyID policy_script_file metadata_file (comm_/config_mint :basePath)  (comm_/config_mint :mint_wallet_address_file)  nft_amount sub_path)
                estimated_fee  (estimated_build_fee (:out   tx_build_result))]
          (info tx_build_result)
          (info "build over")

          ;;返回  {:result true/false}
          (check_sh_result  (sign_tx_submit (comm_/config_mint :basePath)  sub_path))
          )
          (catch clojure.lang.ExceptionInfo e (error "catch e: " e)
                 ;;
                  {:result false}
                 )
          )

        )
    )
  )





(defn -main [& args]
  (info "input args []
            args[0]: json_file
   ")
  (if (<  (count args) 1)
    (info "error not enough input parameters")
    (do
      (action (nth args 0))
      )
    )
  )
