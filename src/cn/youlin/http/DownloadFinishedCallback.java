/**
 * 
 */
package cn.youlin.http;

/**
 * 接口功能：下载回调，需要调用者实现这个接口，供下载任务完成时回调
 * @author liwei
 * @version 1.1
 */
public interface DownloadFinishedCallback {
	/**
	 * 下载完成回调方法
	 * @param status 下载状态，通知调用者下载成功与否的状态标记
	 * @param strMsg 下载消息，通知调用者下载结果中的描述信息
	 * @return 返回boolean类型，返回true表示下载成功，返回false表示下载失败
	 */
	public boolean downloadFinished(boolean status, String strMsg);
}
