package com.groom.platform.datasource

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 트랜잭션 readOnly 속성에 따라 Primary/Replica를 자동으로 라우팅하는 DataSource
 *
 * **사용법:**
 * ```kotlin
 * @Transactional(readOnly = false)  // MASTER로 라우팅
 * fun createOrder(order: Order) { ... }
 *
 * @Transactional(readOnly = true)   // REPLICA로 라우팅
 * fun getOrders(): List<Order> { ... }
 * ```
 *
 * **라우팅 규칙:**
 * - @Transactional(readOnly = true) → REPLICA
 * - @Transactional(readOnly = false) → MASTER
 * - 트랜잭션 없음 → MASTER (기본값)
 *
 * @see DataSourceType
 */
class DynamicRoutingDataSource : AbstractRoutingDataSource() {

    /**
     * 현재 트랜잭션 상태에 따라 사용할 DataSource를 결정합니다.
     *
     * @return DataSourceType.MASTER 또는 DataSourceType.REPLICA
     */
    override fun determineCurrentLookupKey(): DataSourceType {
        // 트랜잭션이 활성화되어 있는지 확인
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // 읽기 전용 트랜잭션인지 확인하여 DataSource 결정
            val isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            return DataSourceType.isReadOnlyTransaction(isReadOnly)
        }

        // 트랜잭션이 없으면 기본값(MASTER) 사용
        return DataSourceType.MASTER
    }
}

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
