package com.hypershare.application

import com.hypershare.model.TransferJob
import com.hypershare.model.TransferPriority
import com.hypershare.model.TransferState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.PriorityQueue

class TransferQueue {

    private val queue = PriorityQueue<TransferJob> { j1, j2 ->
        j1.priority.value.compareTo(j2.priority.value)
    }

    private val _activeJobs = MutableStateFlow<List<TransferJob>>(emptyList())
    val activeJobs: StateFlow<List<TransferJob>> = _activeJobs.asStateFlow()

    fun enqueue(job: TransferJob) {
        synchronized(queue) {
            queue.add(job)
            _activeJobs.value = queue.toList()
        }
    }

    fun dequeue(): TransferJob? {
        return synchronized(queue) {
            val job = queue.poll()
            _activeJobs.value = queue.toList()
            job
        }
    }

    fun updateJobState(jobId: String, newState: TransferState, bytesTransferred: Long = 0L) {
        synchronized(queue) {
            val list = queue.toList().map {
                if (it.id == jobId) {
                    it.copy(state = newState, bytesTransferred = bytesTransferred)
                } else it
            }
            queue.clear()
            queue.addAll(list)
            _activeJobs.value = list
        }
    }
}
