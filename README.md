# Task mint assets

 

功能：读取json本地文件，调用 cardano-cli来生产nft.目前只支持testnet



clojure版本：org.clojure/clojure "1.10.1"

入口main文件：nmint.clj

直接使用lein来打包

编译

```
lein do clean, uberjar
```



### 配置





config/mint.edn

```
{
 :cardano_cli "/home/ubuntu/.local/bin/cardano-cli"
 :mint_wallet_address_file "/home/ubuntu/test_cardano/keys/payment1.addr"
 :basePath "......../order_jsons/2022_3"
 :policy_before_slot 59310349
 :paymentsky_file_ "/home/ubuntu/test_cardano/keys/payment1.skey"
 :protocol_file  "/home/ubuntu/install/temp/protocol.json"
 :order_path "......../order_jsons/2022_3"
 :lock_policy_file   ".........../p.lock"
 :lock_send_file     "........./s.lock"
 :previous_policyID_file "......../previousPID"
 :send_everyfile_sleep 5000
 :policy_everyfile_sleep 5000
 
}

```

cardano_cli就是cardano-cli 的命令目录

mint_wallet_address_file 后台mint的钱包地址

basePath 就是那些用户提交的order存放的mint数据的基础目录

policy_before_slot 有效期设置 

paymentsky_file_ 自己mint的私钥

protocol_params_file 是整个网络的信息，需要手动下载,下面命令得到

```
cardano-cli query protocol-parameters $testnet --out-file protocol.json
```

order_path和basePath 相同

剩下的都是一些基础配置，字面意思就可以理解





policy.clj 文件：

需要blockfrost账号project id，接口调用，检查是否输入有效的policy id. 这里需要配置的，现在分散在代码里，需要大家关键词“project_id”去搜索，然后修改成自己的project id

```
(defn is_policy_online? [policyID nft_name]
  (let [
        asset (str policyID nft_name)
        url_  (str "https://cardano-testnet.blockfrost.io/api/v0/assets/" asset)  ]

    (info url_)
    (let [result_ (try
                    (count 
                     (get 
                      (client/get url_
                                  {:headers {:project_id "testnetxxxxxxx"}})
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
```



定时任务使用crontab配置

```
*/2 * * * * sh  xxxxxxxx/clj/run.sh
```

不能太频繁去执行，每两分钟执行一次。原因是如果上次的提交没有执行完会报错，当然这里报错是没关系的，只是没有成功。

run.sh

```
/usr/bin/java -jar mint-0.1.0-SNAPSHOT-standalone.jar &
```

