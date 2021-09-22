package tinker.sample.android.api.param;

import com.google.gson.annotations.SerializedName;

/**
 * 该类封装了开始游戏所需要的参数
 * 客户端需要根据实际需求自定义业务后台的请求参数
 * 业务后台API文档：https://cloud.tencent.com/document/product/1162/40740
 */
public class GameStartParam {
    @SerializedName("GameId")
    public String gameId;
    @SerializedName("ClientSession")
    public String clientSession;
    @SerializedName("UserId")
    public String userId;

    /**
     * @param gameId 游戏代码
     * @param clientSession 客户端请求参数
     * @param userId 用户id
     */
    public GameStartParam(String gameId, String clientSession, String userId) {
        this.gameId = gameId;
        this.clientSession = clientSession;
        this.userId = userId;
    }
}
