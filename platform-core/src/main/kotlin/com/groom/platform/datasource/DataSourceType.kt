package com.groom.platform.datasource

/**
 * DataSource 타입을 나타내는 Enum
 *
 * - MASTER: 쓰기 작업용 Primary Database
 * - REPLICA: 읽기 작업용 Replica Database
 */
enum class DataSourceType {
    /**
     * Primary Database (쓰기 작업)
     */
    MASTER,

    /**
     * Replica Database (읽기 작업)
     */
    REPLICA,
    ;

    companion object {
        /**
         * 트랜잭션의 readOnly 속성에 따라 DataSourceType을 반환합니다.
         *
         * @param txReadOnly 트랜잭션의 readOnly 속성
         * @return readOnly=true면 REPLICA, false면 MASTER
         */
        fun isReadOnlyTransaction(txReadOnly: Boolean): DataSourceType =
            if (txReadOnly) REPLICA else MASTER
    }
}
