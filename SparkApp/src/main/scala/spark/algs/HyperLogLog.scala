package spark.algs
/**
	* 基数统计基本概念
	* 基数统计（Cardinality Counting）用于统计一个集合中不重复元素的个数，如：网站UV。
	*
	* 最简单做法记录一个不重复元素集合Su，当新增一个元素x，若Su中不包含该元素x则将其加入Su中，否则不加入。最后Su中元素个数即为计数值。
	* 问题1：在大数据环境中，内存空间呈线性增长；
	* 问题2：集合Su不断增大，判断元素x是否则Su内时间复杂度增加；
	* 问题3：大数据背景下，可能需要多个独立的计数值进行聚合，复杂度会更大；
	*
	* 计数统计方法：
	* 1、B树，优势是插入和查找，对于计数只需要计算叶节点的数据，但大数量情况下这些数据会占用大量内存没法节省空间。
	* 2、Bitmap，通过bit数据存储特定数据的一种数据结构，每一个bit位代表一条数据。例如bit数组001101001代表数据[2,3,5,8]。新加入一个
	*    元素只需要把已有的bit数组和新加入额数字按位或计算。bitmap中1的数量为集合的基数值。
	*   Bitmap的优势是可以很容易合并多个统计结果，只需要对多个结果异或操作，可以减少内存。
	*    -- 1亿个数据的基数值需要内存：100000000/8/1024/1024 = 12M
	*    -- 若用32bit的Int代表每个统计数据，需要内存：32*100000000/8/1024/1024 =381M
	*   统计一个对象的基数值需要12M，如果统计10000个对象，就需要将近120G了，同样不能广泛用于大数据场景
	*
	* 概率算法
	* 在大数据环境中精确计算基数的高效算法还没有出现，而在不追求绝对准确的情况下，使用概率算法是一个不错的解决方案。概率算法不直接存储数据
	* 集合本身，通过一定的概率统计方法预估基数值，这种方法可以节省内存同时保证误差控制在一定范围内。
	* 基于概率的统计算法：
	* 1、Linear Counting(LC)：LC在空间复杂度方面不算太好，与Bitmap差不太多，为O(Nmax)
	* 2、LogLog Counting(LLC)：LogLog Counting相比于LC更加节省内存，空间复杂度只有O(log2(log2(Nmax)))
	* 3、HyperLogLog Counting(HLL)：HyperLogLog Counting是基于LLC的优化和改进，在同样空间复杂度情况下，能够比LLC的基数估计误差更小。
	*
	* HyperLogLog
	*  <a>HyperLogLog the analysis of a near-optimal cardinality estimation algorithm</a>
	* 用bitmap统计1亿个基数的数据需要使用内存12M，而HLL算法只需不要1K的内存就能做到，在Redis中实现的HyperLogLog只需要12K内存，
	* 在标准误差0.81%的前提下，能够统计2**64个数据。
	*
	* HLL中实际存储的是一个长度为m的大数组S，将待统计的数据集合划分成m组，每组根据算法记录一个统计值存入数组中。数组的大小m由算法
	* 实现方自己确定，redis中这个数组的大小是16834，m越大，基数统计的误差越小，但需要的内存空间也越大。
	*
	* ------------------
	* HyperLogLog原理
	* ------------------
	* 以抛硬币为例，出现正反面的概率都是1/2，一直抛硬币直到出现正面，记录下投掷次数k，这种抛硬币多次直到出现正面的过程即为一次伯努力过程。
	* 对于n次伯努力过程，我们得到n个出现正面的投掷次数：K1，K2，...，Kn，记其中最大值为Kmax，则有以下结论：
	* 	1、n次伯努力过程的投掷次数都不大于Kmax；
	* 	2、n次伯努力过程至少有一次投掷次数等于Kmax；
	* 则用数学表达式表示为：
	* 	Pn(X <= Kmax) = (1 - 1/2**Kmax)**n
	* 	Pn(X >= Kmax) = 1 - (1 - 1/2**(Kmax-1))**n
	* 当 n<<2**Kmax时，Pn(X >= Kmax)约为0，即当n远小于2**Kmax时上述第1个结论不成立。
	* 当 n>>2**Kmax时，Pn(X <= Kmax)约为0，即当远大于2**Kmax时上述第2个结论不成立。
	*
	* 结论：可以用2**Kmax来估计n的大小。进行了n次抛硬币实验，每次分别记录下第一次抛到正面的抛掷次数K，那么可以用实验中最大的抛掷次数Kmax来
	* 估算实验组数量n约等于2**Kmax。
	*
	* 在HyperLogLog基数统计中，要统计一组数据不重复的元素个数，首先集合中每个数据经过HASH函数处理后得到一个二进制串，类比抛硬币实验，二进制
	* 串中1代表正面，0代表背面，二进制串中低位开始第一个出现1的位置可以理解为抛硬币第一次出现正面的次数，因此通过多次实验HASH处理可以得到二进制
	* 中出现第一个1时的最大位置Kmax，从而实现对整体基数的估计。
	*
	* HyperLogLog核心在于观察集合中每个数字对应的比特串，通过统计和记录比特串中最大的出现1的位置来估计集合整体的基数，可以大大减少内存耗费。
	*
	* ------------------
	* HyperLogLog分桶平均
	* ------------------
	*	HLL利用集合元素的二进制中第一次出现1的最大次数预估整体基数，但误差较大，为了改善误差HLL提出分桶平均。
	*	例如以抛硬币为例，第一次试验抛出10次就得到正面则得到的结果误差很大，若进行100组同时抛硬币，那么就得到100组出现1的次数，降低误差，
	*	可以通过100组试验结果的平均值到一个平均值来预估整体的基数。
	*
	*	分桶平均的基本原理是将统计数据划分为m个桶，每个桶分别统计各自的Kmax并能得到各自的基数预估值，最终对这些预估值求平均得到整体的基数估计值。
	*		-- LLC中使用几何平均数预估整体的基数值，但是当统计数据量较小时误差较大；
	*		-- HLL在LLC基础上做了改进，采用调和平均数，调和平均数的优点是可以过滤掉不健康的统计值；
	*
	*
	* HypperLogLog预估整体的计算公式：
	*
	*	                    DV = constant * m * H
	*	其中：
	*		1、constant为修正常数；
	*		2、m为分桶数
	*   3、H为每个桶计算结果的调和平均值
	*                            m
	*          H =    --------------------------
	*                   sum{j=1 to m}(1/2**Rj)
	*
	*			Rj为第j个桶中的元素二进制中第一次出现1的位置 + 1
	*     (1/2**Rj)为每个桶的估计值
	*     sum{j=1 to m}(1/2**Rj)所有桶的调和平均值
	*
	* 统计数组大小m越大，基数统计的标准误差越小，但需要的存储空间也越大，在m=2**13的情况下，HLL的标准误差为1.1%。
	*
	* -----------------------
	* HyperLogLog修正与参数确定
	* -----------------------
	*	通过数学分析可以知道上述估计值并不是基数n的无偏估计，因此需要修正成无偏估计，可以采用分段偏差修正具体参考论文。
	*	例如：在数据总量比较小的时候，很容易就预测偏大，进行如下调整：
	*							if (DV < (5 / 2) * m) DV = m * log(m/V)
	*
	*	其中DV代表估计的基数值，m代表桶的数量，V代表结果为0的桶的数目，log表示自然对数。
	*
	*	解释V的含义：
	*	假设我分配了m=64个桶，当数据量很小时，如三个，那肯定有大量桶中没有数据，即他们的估计值是0，V就代表这样的桶的数目。
	*
	* 【constant常数的选择】：m为分桶数，p是m的以2为底的对数
	*             p = log2(m)
	*			则有
	*         p = 4时：constant = 0.673 * m * m
	*         p = 5时：constant = 0.697  * m * m
	*         p = 6时：constant = 0.709  * m * m
	*         p = 其他时：constant = (0.7213 / (1 + 1.079 / m))  * m * m
	*
	* 【分桶数m的选择】：
	*		分桶数只能是2的整数次幂，如果分桶越多，那么估计的精度就会越高，统计学上用来衡量估计精度的一个指标是“相对标准误差”(relative standard deviation，简称RSD)，
	*	RSD的值其实就是（(每次估计的值）在（估计均值）上下的波动）占（估计均值）的比例。RSD的值与分桶数m存在如下的计算关系：
	*							      1.04
	*						RSD = ---------
	*					         sqrt(m)
	*
	* -----------------------
	*  基于HLL的redis实现
	* -----------------------
	*  redis中统计数组大小设置为 m = 2**14 = 16384，hash函数生成64位bit数组，其中14位用来找到统计数组的位置，剩下50位用来
	*  记录第一个1出现的位置，最大位置为50，需要log2(50)=6位记录。
	*  那么统计数组需要的最大内存为：6bit * 16384 = 12K
	*/
object HyperLogLog extends App {

}
