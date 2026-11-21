package g9.enrollflow;

/**
 * 玩家操作的種類，存在 {@link Action} 裡，給「復原上一步」判斷要做哪一種反向操作。
 */
public enum ActionType {
    /** 加選；復原時退選。 */
    ADD,
    /** 退選；復原時重新加選，名額已被遞補時改排候補。 */
    DROP,
    /** 排候補；復原時取消候補。 */
    JOIN_WAIT,
    /** 取消候補；復原時重新排到隊尾（原順位拿不回來）。 */
    LEAVE_WAIT
}
