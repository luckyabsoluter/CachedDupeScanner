package opensource.cached_dupe_scanner.ui.home

internal data class ResultsQueryToken(
    val requestId: Long,
    val queryVersion: Long
)

internal class ResultsScreenDbQueryCoordinator {
    private var requestSequence: Long = 0L
    private var queryVersion: Long = 0L
    private var activeRefreshRequestId: Long = 0L
    private var activePagingRequestId: Long = 0L

    fun beginRefresh(reset: Boolean): ResultsQueryToken {
        val requestId = nextRequestId()
        activeRefreshRequestId = requestId
        if (reset) {
            queryVersion += 1L
            // Reset query invalidates all in-flight paging responses.
            activePagingRequestId = 0L
        }
        return ResultsQueryToken(requestId = requestId, queryVersion = queryVersion)
    }

    fun beginPaging(): ResultsQueryToken {
        val requestId = nextRequestId()
        activePagingRequestId = requestId
        return ResultsQueryToken(requestId = requestId, queryVersion = queryVersion)
    }

    fun isRefreshTokenValid(token: ResultsQueryToken): Boolean {
        return token.requestId == activeRefreshRequestId && token.queryVersion == queryVersion
    }

    fun isPagingTokenValid(token: ResultsQueryToken): Boolean {
        return token.requestId == activePagingRequestId && token.queryVersion == queryVersion
    }

    fun currentQueryVersion(): Long = queryVersion

    private fun nextRequestId(): Long {
        requestSequence += 1L
        return requestSequence
    }
}
