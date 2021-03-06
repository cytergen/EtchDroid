package eu.depau.etchdroid.utils.imagetypes

import eu.depau.etchdroid.utils.enums.PartitionTableType
import eu.depau.etchdroid.utils.Partition

interface Image {
    val partitionTable: List<Partition>?
    val tableType: PartitionTableType?
    val size: Long?
}