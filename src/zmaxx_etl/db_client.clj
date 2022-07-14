(ns zmaxx-etl.db-client
  (:use
   [clojure.tools.logging :only (info error)]
   )
  (:require
   [zmaxx-etl.common :as comm_]
            )
  (:import com.mchange.v2.c3p0.ComboPooledDataSource)
  (:gen-class))





(require '[clojure.java.jdbc :as j])
(use 'clojure.set)
(require '[clojure.string :as str])
(import java.text.SimpleDateFormat)



(def mysql-db {:subprotocol "mysql"
               :subname (:mysql_uri comm_/configmy)
               :user (:db_user  comm_/configmy)
               :password (:db_password  comm_/configmy)})



(defn pool
  [spec]
  (let [cpds (doto (ComboPooledDataSource.)
               (.setDriverClass (:classname spec))
               (.setJdbcUrl (str "jdbc:" (:subprotocol spec) ":" (:subname spec)))
               (.setUser (:user spec))
               (.setPassword (:password spec))
               (.setInitialPoolSize 1)
               (.setIdleConnectionTestPeriod 10)
               (.setPreferredTestQuery "select 1")
               (.setAcquireIncrement 2)
               (.setMaxPoolSize 50)
               (.setMinPoolSize 1)
               (.setMaxStatements 5)
               (.setMaxStatementsPerConnection 100)
               ;; expire connections after 3 hours of inactivity:
               (.setMaxIdleTime 50))]
    {:datasource cpds}))

(def pooled-db (delay (pool mysql-db)))


(defn db-connection [] @pooled-db)


;;-------sale--------



(defn sale_common_insert [sql_in]
  (info sql_in)
  (try
             (j/execute!  (db-connection)
                          [sql_in])
             (catch Exception e
               (error (.getMessage e)))
             )
  )


;;---merchandise---
(defn  merchan_comm [sql_str in_fn]
  (info sql_str)
  (try
    (j/query  (db-connection)
              [sql_str]
              :row-fn  in_fn
              )
    (catch Exception e
      (do
        (error  (.getMessage e))
        (throw (Exception. e))
        ))
    )
  )


(def insert_m_sql "
 INSERT INTO `rb_merchandise_store` ( 
				     `store_id`, 
                                     `store_name`,
                                     `sum_jj`, 
                                     `sum_payment`,
                                     `gross_profit`,
                                     `sum_qty`, 
                                     `unit_merchan_price`,
                                      `order_count`, 
                                     `unit_order_price`, 
                                     `joint_rate`, 
                                     `modify_date`)
                                     VALUES
                                     (%s, '%s', %s, %s, %s, %s, %s, %s, %s, %s,  now());

")





;;添加数据
(defn merchandise_insert [input_map]
  (try
             (j/execute!  (db-connection)
                          [
                           (let [sql (format insert_m_sql
                                             (get input_map "store_key")
                                             (get input_map "name")
                                             (get input_map "jj")
                                             (get input_map "payment")
                                             (Float/parseFloat (format "%.2f" (get input_map "gross_profit")))
                                             (get input_map "qty")
                                             (get input_map "unit_merchan_price")
                                             (get input_map "order_count")
                                             (get input_map "unit_order_price")
                                             (Float/parseFloat (format "%.2f" (get input_map "joint_rate")))
                                                                                          
                                             )]
                             (info sql)
                             sql
                             )
                            ])
             (catch Exception e
               (error (.getMessage e)))
             )
  )


(defn merchandise_cate_insert [sql_in input_map]
  (try
             (j/execute!  (db-connection)
                          [
                           (let [sql (format sql_in
                                             (get input_map "c1name")
                                             (get input_map "c2name")
                                             (get input_map "c3name")
                                             (get input_map "cid")
                                             (get input_map "payment")
                                             (Float/parseFloat (format "%.2f" (get input_map "gross_profit")))
                                             (Float/parseFloat (format "%.2f" (get input_map "gross")))
                                                                                          
                                             )]
                             (info sql)
                             sql
                             )
                            ])
             (catch Exception e
               (error (.getMessage e)))
             )
  )


(defn  merchan_get_store [sql_str]
  (try
    (j/query  (db-connection)
              [sql_str]
              :row-fn  #(hash-map    "store_id" (:id %)  "store_name"  (:name %))
              )
    (catch Exception e
      (do
        (error  (.getMessage e))
        (throw (Exception. e))
        ))
    )
  )






;;--------------


(defn  get_sale_daily [sql_str]
  (try
    (j/query  (db-connection)
              [sql_str]
              :row-fn  #(hash-map    "order_id" (:order_id %)  "create_dt"  (:create_dt %) "jj"  (:jj %)  "lsj" (:lsj %) "sku_pay" (:sku_pay %) )
              )
    (catch Exception e
      (do
        (error  (.getMessage e))
        (throw (Exception. e))
        ))
    )
  )



(def sql_str "select rdp.jj,rdp.lsj,orderI.order_id, DATE_FORMAT(orderI.create_dt,'%Y-%m-%d') as create_dt , orderI.store_key,orderI.spu_key,orderI.sku_id, orderI.sku_price,orderI.sku_pay,orderI.sku_qty,orderI.store_key  from zmaxx_001.order_item as orderI
left join  rd_product as rdp on rdp.id = orderI.sku_id
where   orderI.order_source='redian' and orderI.sku_pay>0
and  orderI.create_dt >=  '2021-05-10 10:30:04' and orderI.create_dt <= '2021-05-13 10:30:04'
order by create_dt desc limit 10
")


(defn sale_table []
(let [tom (get_sale_daily)]
  (let [basic_info
        (conj
         {:orderid_count (let [order_id_count   (count (distinct  (map (fn [x] (get   x "order_id")) tom)))]
                           order_id_count
                           )}
         {:items_count (count tom)}
         {:cbje_sum (apply  +  (map (fn [x] (get x "jj")) tom))}
         {:lsje_sum (apply  +  (map (fn [x] (get x "lsj")) tom))}
         {:pay_sum (apply  +  (map (fn [x] (Double/valueOf (get x "sku_pay"))) tom))}
         )]
    (conj  basic_info
           {:average_item_price (float  (/  (:pay_sum basic_info)   (:items_count basic_info)))}
           {:average_order_price (float  (/  (:pay_sum basic_info)   (:orderid_count basic_info)))}
           ;;毛利率
           {:gross_profit (float  (/ (- (:pay_sum basic_info) (:cbje_sum basic_info))   (:orderid_count basic_info)))}
           ;;连带率Joint and several rate
           {:joint_rate (float  (/ (:items_count basic_info) (:orderid_count basic_info) ))}           
           )
    )
  )
  )










;;---------------------


(defn query_ids [sql_str]
   (try
    (j/query  (db-connection)
              [sql_str]
              :row-fn  #(conj  (:cid %) )
              )
    (catch Exception e
      (do
        (error  (.getMessage e))
        (throw (Exception. e))
        ))
    )
  ) 


(defn get_cate_topids []
  (map #(get % "cid")       
   (try
     (j/query  (db-connection)
               ["select value_str from dmp_value where var = 'pss_categroy'"]
               :row-fn  #(hash-map    "cid" (:value_str %)  )
               )
     (catch Exception e
       (do
         (error  (.getMessage e))
         (throw (Exception. e))
         ))
     ))
  )

(def sql_cate_ids "
 select rd_product_category.id as cid from rd_product_category where rd_product_category.upnum in
	(select rd_product_category.num from rd_product_category where rd_product_category.upnum in						
		(select num from rd_product_category where num in
			('%s')));	
")

;;返回已经 {top_cid p_id}
(defn build_cate_map []
  (let [cid_set (get_cate_topids)]
    (reduce into {}
            (map hash-map
                 cid_set
                 (map #(query_ids %) 
                      (map #(format sql_cate_ids  %) cid_set))
                 ))
    )
  )

(def  sql_price_cate "
select  sum(CAST(orderI.sku_qty AS DECIMAL(12,2)) ) as qty ,sum(CAST(tempP.jj AS DECIMAL(12,2)) ) as jj,	sum( CAST(tempP.lsj AS DECIMAL(12,2))) as lsj,sum(CAST(orderI.sku_pay AS DECIMAL(12,2)) ) as payment
from zmaxx_001.order_item as orderI
right join 
( 
    #符合分类条件的商品信息 
	select rdp.lsj, rdp.name , rdp.jj,rdp.catid, rdp.id as pid ,rdpc.name as  catname  from rd_product as rdp   left join rd_product_category as rdpc  
	on rdpc.id = rdp.catid  
	where rdp.catid in ( 
					%s					)
						
)
as  tempP on tempP.pid=orderI.sku_id
where   orderI.order_source='redian' and orderI.sku_pay>0 
and  orderI.create_dt >=  '2021-05-11 10:30:04' and orderI.create_dt <= '2021-05-13 10:30:04'
order by create_dt desc
;
")


(defn get_price_table [str_sql]
    (try
      (j/query  (db-connection)
                [str_sql]
                :row-fn  #(hash-map    "qty"  (if (nil? (:qty %)) 0 (:qty %) )
                                       "jj"  (if (nil? (:jj %)) 0 (:jj %) )
                                       "lsj"  (if (nil? (:lsj %)) 0 (:lsj %) )
                                       "payment"  (if (nil? (:payment %)) 0 (:payment %) )
                                       )
                )
      (catch Exception e
        (do
          (error  (.getMessage e))
          (throw (Exception. e))
          ))
      )
    )




(defn price_table []
  (let [cate_map (build_cate_map)]
    (let  [top_cids (keys cate_map)]
      top_cids
      (let [result_sum 
            (map #(conj {"top_cid" %}
                        (first  (get_price_table   (format sql_price_cate (str/join ","  (get cate_map %)))))
                        ) top_cids)]
        ;;result_sum
        ;;总数量
        (let [qty_sum
              (reduce + 
                      (map (fn [x]  (get  x  "qty")) result_sum  #_(map #(get % "v") result_sum)))
              pay_sum
              (reduce + 
                      (map (fn [x]  (get  x "payment")) result_sum #_(map #(get % "v") result_sum)))              
              ]

          ;;result_sum
          (map (fn [x] (merge  {"pay"  (get x "payment")}
                               {"pay_rate" (if  (= 0  (get x "payment")) 0  (float (/ (long (get x "payment")) (long pay_sum))))}
                               {"unit_price" (if  (= 0 (get x "qty")) 0  (float (/  (long (get x "payment"))  (long  (get x "qty"))  ))) }
                               {"gross_profit" (if  (= 0   (get x "jj")) 0  (float (/  (long (- (get x "payment") (get x "jj")))   (long  (get x "payment"))))  )  }
                               {"pay_sum" pay_sum})

                 )
               result_sum)
          
          )
        )
      )
    )
  )


(defn price_table_check []
  (let [cate_map (build_cate_map)]
    (let  [top_cids (keys cate_map)]
      top_cids
      (let [result_sum 
            (map #(conj {"top_cid" %}
                        (hash-map "v" 
                                  (get_price_table   (format sql_price_cate (str/join ","  (get cate_map %))))
                                  )
                        ) top_cids)]
        result_sum
        ;;总数量
        #_(let [qty_sum
              (map (fn [x]  (get  (first x) "qty")) (map #(get % "v") result_sum))
              ;;(reduce +  )
              ]
          qty_sum
          
          )
        )
      )
    )
  )







