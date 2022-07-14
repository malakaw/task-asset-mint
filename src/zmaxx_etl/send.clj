(ns zmaxx-etl.send
 (:gen-class)
  (:require
    [zmaxx-etl.common :as comm_]
    [zmaxx-etl.policy :as policy_]
    [clj-http.client :as client]
    [clojure.java.io :as io]

            )
  (:use
   [clojure.tools.logging :only (info error)]
   )
  )

(use '[clojure.java.shell :only [sh]])
;;(require '[clojure.java.io :as io])
(require '[clojure.data.json :as json])





(def cardano_cli (comm_/config_mint :cardano_cli))
(def policy_key ["policy.vkey" "policy.skey"])






(def value_map
  {:invalid-hereafter    59310349
   ;;:from_wallet_address  "/home/ubuntu/test_cardano/keys/payment1.addr"
   :paymentsky_file       (comm_/config_mint :paymentsky_file_)
   :cardano_cli           (comm_/config_mint :cardano_cli)
   :policy_vkey_fname    "policy.vkey"
   :policy_skey_fname    "policy.skey"
   ;;必须要发送的ada
   :send_ada_force       1500000
  ;; :temp_fee             300000
   :rec_matx_file        "rec_matx.raw"
   :rec_sig_matx_file    "rec_sig_matx.raw"
   :protocol_params_file  (comm_/config_mint :protocol_file)
   }
  )



(def command_build_raw
;;  [cardano_cli "transaction" "build-raw"  " --testnet-magic 1097911063"]
  [cardano_cli "transaction" "build-raw"  "  --alonzo-era"]
  )


#_(def command_get_tx
  [cardano_cli "query" "utxo" "--testnet-magic" "1097911063" "--address" '(slurp (:from_wallet_address value_map) )
   ])

(def command_build_sign
  [cardano_cli "transaction" "sign"
   "--signing-key-file" ""
   "--signing-key-file" ""
   "--testnet-magic 1097911063 --tx-body-file" ""
   " --out-file " ""])


(def command_submit
  [cardano_cli
   " transaction submit --tx-file " ""
   " --testnet-magic 1097911063"])




(def command_get_tx
  [cardano_cli "query" "utxo" "--testnet-magic" "1097911063" "--address"])


;;计算费用的逻辑
;;TxHash Amount— fee— min Send 1.5 ada in Lovelace=the output for our own address
(defn calculate_from_ada_amount [all_amount fee ]
  (- all_amount fee  (:send_ada_force value_map))
  )



(defn get_long_tokens [txs]
  (map
     (fn [x]
      (clojure.string/join " " (drop-last 2 (drop 5 x)))
     )
     (remove (fn [x] (< (count x) 7)) txs))
  )


(def command_calculate-min-fee
  [cardano_cli
   "transaction"
   "calculate-min-fee"
   "--tx-body-file"
   ""
   "--tx-in-count"
   ""
   "--tx-out-count"
   ""
   "--witness-count 1  --testnet-magic 1097911063 --protocol-params-file "
   ;;protocol.json
   ""
   "  | cut -d ' '  -f1"
   ]
  )

(defn  calculate_mini_fee [tx-raw-body-file tx-in-count tx-out-count]
  (let [shell_ (clojure.string/join " "
                                    (assoc command_calculate-min-fee 4  tx-raw-body-file 6 tx-in-count 8 tx-out-count 10 (:protocol_params_file value_map)))]


    (info "-->shell")
    (info shell_)
    (let [out_ (:out
                (sh "bash" "-c"  shell_))]
      (info "-->out:")
      (info out_)
      (Long/valueOf
       (clojure.string/replace
        out_
        #"\r?\n" ""
        ))
      )
    )

  )



;;发送 5cddfba831cdca6199681748d2a78bb97cdfbf69c54b82f205d4e61a.4e465457584a， 这个token 2个到别的钱包地址，这个地址变成6个token；
(defn exchange_token [from_addr to_addr pid_tokenname  send_amout txs fee]
  (let [ada_amount  (apply + (map (fn [x] (Long/valueOf  (nth x 2))) txs))
        long_tokens_line (first (get_long_tokens txs))
        raw_list_tokens (clojure.string/split long_tokens_line #" \+ ")
        list_tokens (map (fn [x] (clojure.string/split x #" ")) raw_list_tokens)
        map_tokens (apply hash-map (reverse  (apply concat  list_tokens)))
        ]

    (info map_tokens)
    (info pid_tokenname)
    (if (contains? map_tokens pid_tokenname)
      (let [process_token_amount (Long/valueOf (get map_tokens pid_tokenname))]
        (println process_token_amount)
        ;;判断是否有足够的token数量可以trsaction
        (if (> send_amout  process_token_amount)
          (do
            (info "error: send_amout big than  process_token_amount")
            ;;(System/exit 0))

            )
          (let [new_map (dissoc map_tokens pid_tokenname)
                new_list (map (fn [x] (str (first x) " " (second x)))    (partition 2   (reverse (reduce concat new_map))))
                str_from_out_tx_clean  (clojure.string/join " + " new_list)
                str_from_out_tx    (str "--tx-out '" from_addr "+"  (calculate_from_ada_amount  ada_amount fee) "+"  (- process_token_amount send_amout) " " pid_tokenname  " + " str_from_out_tx_clean "'")
                str_to_out_tx (str " --tx-out '" to_addr "+" (:send_ada_force value_map) "+" send_amout " " pid_tokenname "'")

                ]

            {:out_tx_key (list str_from_out_tx str_to_out_tx)
             :adaamount_key ada_amount}
            )
          )
        )
      "not has"
    )
  )
)

#_(build_raw_tx
  "/home/ubuntu/install/temp"
  "t17"
  "addr_test1vrfkq33rrsczulhumfssfa4cqadwzp0clh85nyjvhl0eysglfdkvs"
  "addr_test1qqldhuqh8xgpuwms5tc30a7xwque475h05dmj8ves6trj05tefmez86m7fuvc9m7yhraxwhytunf8x0dyf6uqs62ygusvkwt40"
  "5cddfba831cdca6199681748d2a78bb97cdfbf69c54b82f205d4e61a"
  "4e465457584a"
  3
  )

;;--------------sign tx----------------------
;;生成签名文件 matx.signed
(defn sign_tx_submit [base_path sub_path]
  (let [paymentskey_file (:paymentsky_file value_map)
        policyskey_file (str  base_path  "/" sub_path "/policy.skey")
        raw_file (str base_path   "/" sub_path  "/" (:rec_matx_file value_map))
        sign_file (str base_path  "/" sub_path  "/" (:rec_sig_matx_file value_map))]
    ;;sign
    (let [result_sign (sh "bash" "-c" (clojure.string/join " "
                                                           (assoc command_build_sign 4 paymentskey_file 6 policyskey_file 8 raw_file 10 sign_file )))]
      (info  (str "-in:" "sign_tx_submit [1] " ))
      (info result_sign)
      (info  (str "-in:" "sign_tx_submit [2] " ))

      (let [submit_result (sh "bash" "-c" (clojure.string/join " "
                                                               (assoc command_submit 2 sign_file )))]

        (info submit_result)
        (info "finally  done!")
        )

      )
    )
  )




(defn build_raw_tx [base_path sub_path from_addr to_addr policyID tokenname  send_amout fee_]
  (let [tx_long_string            (:out (apply sh  (conj  command_get_tx  from_addr)))
        txs                       (map (fn [x] (remove clojure.string/blank?  (clojure.string/split x #" ")))
                                       (drop 2 (clojure.string/split-lines  tx_long_string)) )
        list_txs                  (map (fn [x]  (str (nth x 0) "#" (nth x 1))) txs)
        tx_in_count               (count list_txs)
        tx_out_count              2
        cmini_fee                 (if (= "0" fee_)
                                    0
                                    (calculate_mini_fee  (str base_path   "/" sub_path  "/" (:rec_matx_file value_map))
                                                       tx_in_count
                                                       tx_out_count
                                                       )
                                    )

        command_build_raw_addfee  (conj command_build_raw " --fee " cmini_fee)

        done_tx_in                (reduce (fn [x y] (conj x "--tx-in" y)) command_build_raw_addfee  list_txs)
        from_address              from_addr
        out_tx_and_adaamount      (exchange_token  from_addr to_addr (str policyID "." tokenname) (Long/valueOf send_amout) txs  cmini_fee)
        adaamount                 (:adaamount_key out_tx_and_adaamount)
        done_tx_out               (:out_tx_key out_tx_and_adaamount)
        out_file                  (list  (str "--out-file " base_path "/" sub_path  "/" (:rec_matx_file value_map)))
        ]


    (let [sh_build_str (clojure.string/join " "
                              (apply conj []

                                     (clojure.string/join " "
                                                          done_tx_in
                                                          )
                                     (clojure.string/join " "
                                                          done_tx_out)
                                     out_file
                                     ))]
      (info (str "build:: " sh_build_str))

      (sh  "bash" "-c"
         sh_build_str
         )
      )
   )
  )






(defn action_main [base_path
                   sub_path
                   from_addr
                   to_addr
                   policyID
                   token_name
                   send_amout]
  (let [tokenname (:out (sh "bash" "-c"  (str  "echo -n '" token_name  "' | xxd -b -ps -c 80 | tr -d '\n'")))
        raw_tx  (build_raw_tx base_path sub_path from_addr to_addr policyID tokenname  send_amout)]

    (if (>   (count   (:err raw_tx))
               0)
        ;;有错误
        (info  raw_tx)
        ;;无错误
        (do
          (info  raw_tx)
          (info "build over ")
          (sign_tx_submit base_path sub_path)
          (info "finally done ")
         ;; (System/exit 0)
          )
        )
    )
  )



(defn is_policy_online? [policyID nft_name]
  (let [
        asset (str policyID nft_name)
        url_  (str "https://cardano-testnet.blockfrost.io/api/v0/assets/" asset)  ]

    (info (str "url [policy id:]"  policyID))
    (info (str "url [nft_name:]"  nft_name))
    (info url_)
    (let [result_ (try
                    (count
                     (get
                      (client/get url_
                                  {:headers {:project_id "testnetSYmyIVNEd8C8cccccb"}})
                      :body
                      ))
                    (catch clojure.lang.ExceptionInfo e (prn "catch e: " e)
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



(defn send_exit_and_unlock []
  (io/delete-file (get comm_/config_mint :lock_send_file))
   (System/exit 0)
  )


(defn action [json_file]
  (let [base_path  (comm_/config_mint :basePath)
        client_address   (first (take-last 3  (clojure.string/split json_file   #"/")))
        order_date         (first (take-last 2  (clojure.string/split json_file   #"/")))
        sub_path         (str client_address  "/"  order_date)
;;        sub_path   (first (take-last 2  (clojure.string/split json_file  #"/")))
        from_addr  (slurp (comm_/config_mint :mint_wallet_address_file))
        to_addr     client_address
        policyID   (slurp (str base_path "/"  sub_path "/policyID" ))
        token_name  (policy_/get_first_nft "nft_name"  json_file)
        tokenname   (:out (sh "bash" "-c"  (str  "echo -n '" token_name  "' | xxd -b -ps -c 80 | tr -d '\n'")))
        send_amout (policy_/get_first_nft "count" json_file)
        has_polcy_now (is_policy_online? policyID tokenname)
        ]


    ;;判断policy是否存在
    (if has_polcy_now
      ;;if----
      (let [raw_tx  (build_raw_tx base_path
                                  sub_path
                                  from_addr
                                  to_addr
                                  policyID
                                  tokenname
                                  send_amout
                                  "0")

            real_raw_tx (build_raw_tx base_path
                                      sub_path
                                      from_addr
                                      to_addr
                                      policyID
                                      tokenname
                                      send_amout
                                      "realfee")]

        (info raw_tx)

        (if (>   (count   (:err real_raw_tx))
                 0)
          ;;有错误
          (info  real_raw_tx)
          ;;无错误
          (do
            (info  real_raw_tx)
            (info "build over ")
            (sign_tx_submit base_path sub_path)
            (info "finally done ")
            ;; (System/exit 0)
            )
          )
        "ok"
        )
      ;;else---
      (do
        (error  (str " policy has not online "))
        ;; (send_exit_and_unlock)
        "fail"
        )
      )
    )
  )

;;参数就一个，order json 文件
(defn -main [& args]
  (action (nth args 0))
  )
