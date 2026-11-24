# RPGPlugin (Paper 1.21.1 – 1.21.11+)

Plugin RPG tùy biến toàn diện cho server Minecraft **Paper 1.21.1 – 1.21.11+** (Java 21): hệ thống cấp độ vô hạn, 8 chỉ số & 3 kỹ năng chủ động cho người chơi, trang bị phẩm chất 0–11 sao, cường hóa không giới hạn ở đe, dân làng biết tự vệ và phản đòn, AI quái/Mini Boss/Boss nâng cao, cùng các sự kiện xâm lược ngẫu nhiên.

> **Repository:** `https://github.com/Nhan-209/pluginRPGminecraft`  
> **Build CI (không cần cài Java/Maven trên máy local):** Vào tab **Actions** trên GitHub -> chọn workflow **Build RPGPlugin** mới nhất -> tải artifact `RPGPlugin-jar` chứa file `RPGPlugin-1.0.0.jar` và chép vào thư mục `plugins/` của server Paper.

---

## Mục Lục
1. [Quy tắc chung cho toàn bộ sinh vật](#1-quy-tắc-chung-cho-toàn-bộ-sinh-vật)
2. [Danh sách Quái & Sinh vật ĐÃ ĐƯỢC CHỈNH CƠ CHẾ RIÊNG](#2-danh-sách-quái--sinh-vật-đã-được-chỉnh-cơ-chế-riêng)
3. [Danh sách Quái & Sinh vật CHƯA ĐƯỢC CHỈNH CƠ CHẾ RIÊNG](#3-danh-sách-quái--sinh-vật-chưa-được-chỉnh-cơ-chế-riêng)
4. [Hệ thống RPG Người Chơi (8 Chỉ số, 3 Kỹ năng, GUI `/rpg`)](#4-hệ-thống-rpg-người-chơi)
5. [Trang bị, Phẩm chất (0–11 Sao), Đe & Toàn bộ Hiệu ứng](#5-trang-bị-phẩm-chất-đe--hiệu-ứng)
6. [Hệ thống Dân Làng (Tự vệ, Gọi Vệ Binh, Giao dịch & Trêu chọc)](#6-hệ-thống-dân-làng)
7. [Sự kiện ngẫu nhiên & Danh sách Lệnh](#7-sự-kiện-ngẫu-nhiên--danh-sách-lệnh)

---

## 1. Quy tắc chung cho toàn bộ sinh vật

Mọi `LivingEntity` (trừ `Player`, minion/pet có metadata `is_minion` hoặc `pet_owner`) khi spawn hoặc ở trong bán kính 40 block quanh người chơi Survival/Adventure đều được gắn cấp độ `[Lv. X]`:

- **Cấp độ sinh vật thường (`MobManager.scaleMob`)**:
  - `Level = 1 + (khoảng cách XZ từ tọa độ 0,0 / 50)`.
  - Giới hạn trần có nhân tính: không vượt quá `Level trung bình người chơi online + 10`.
  - Chỉ số mỗi cấp: **+20% Máu tối đa**, **+10% Sát thương cơ bản**, **+0.2% Tốc độ di chuyển** (trừ `Villager` dùng công thức riêng).
- **Cấp độ Boss (`MobManager.scaleBoss`)**:
  - `Level = Level cao nhất trong server + ngẫu nhiên (10 ~ 99)`.
- **Hiển thị máu**: Khi nhận sát thương, tên quái tự cập nhật `§a[HP/MaxHP HP]`.
- **Quy tắc chung cho toàn bộ `Monster` (`MobListener` & `CombatListener`)**:
  - **Không đánh lẫn nhau** (cả cận chiến lẫn đạn) và **không target `EnderDragon`**, trừ trường hợp `Mini Warden` là pet của người chơi (`pet_owner`).
  - **50% tỉ lệ mặc full giáp** (Iron / Diamond / Netherite), có phẩm chất sao và phù phép (`Protection` tối đa 20, `Thorns` tối đa 10) scale theo độ chênh cấp với người chơi (`5%` tỉ lệ rơi).
  - **30% tỉ lệ cầm `1–10` Totem of Undying ở tay phụ** (`5%` tỉ lệ rơi).
- **Quy tắc chung cho toàn bộ `Animals` (`PassiveMobListener` & `RPGPlugin`)**:
  - Nếu `Level động vật >= Level người chơi - 2`, động vật sẽ **chủ động tấn công / phản công** khi người chơi đứng gần (<10 block) hoặc đánh trúng chúng: gây `2.0 + Lv * 0.5` sát thương và húc.

---

## 2. Danh sách Quái & Sinh vật ĐÃ ĐƯỢC CHỈNH CƠ CHẾ RIÊNG

### 2.1. Boss
| Sinh vật | Cơ chế & Điều chỉnh đã cài đặt |
|---|---|
| **Ender Dragon** | • Scale cấp Boss (`HighestPlayerLv + 10~99`).<br>• Khi vừa mất máu (<99% HP): triệu hồi **3 Mini Wither**.<br>• Dưới 50% HP: triệu hồi **15 Zombie mặc full giáp vàng** + mỗi lần bị đánh có **15%** gọi lại **Ender Crystal** (cooldown 20s) + mỗi đòn đánh thường có thêm **5%** sinh Ender Crystal.<br>• Dưới 40% HP: gọi **Sét đánh thẳng** vào mọi người chơi trong world.<br>• Dưới 30% HP: triệu hồi **10 Mini Phantom**.<br>• Dưới 20% HP: triệu hồi **20 Mini Phantom**.<br>• Dưới 10% HP: triệu hồi **1 Mini Warden**.<br>• Mỗi 100s ở The End (`RPGPlugin`): **triệu hồi đội quân 8 Skeleton** (có Speed II + scale level).<br>• Quái vật khác không bao giờ target Rồng. Chết bằng vũ khí có sao có tỉ lệ nâng sao vũ khí. |
| **Wither** | • Scale cấp Boss (`HighestPlayerLv + 10~99`).<br>• Dưới 99% HP: triệu hồi **3 Mini Wither**.<br>• Dưới 50% HP: nhận **Speed III**, triệu hồi **2 Mini Wither Skeleton** + thả **6 quả TNT**.<br>• Dưới 20% HP: triệu hồi thêm **2 Mini Wither**.<br>• Dưới 10% HP: triệu hồi **1 Mini Warden**.<br>• Mỗi 200s (`RPGPlugin`): **triệu hồi đội quân 12 Skeleton** (trừ `Mini Wither` có `is_minion`).<br>• Chết bằng vũ khí có sao có tỉ lệ nâng sao vũ khí. |

### 2.2. Mini Boss (Giới hạn tối đa 1 Mini Boss trong vùng 3x3 chunk khi spawn tự nhiên)
| Sinh vật | Cơ chế & Điều chỉnh đã cài đặt |
|---|---|
| **Warden** | • Gắn tag Mini Boss (ngoại trừ bản `Mini Warden` làm minion/pet).<br>• Khi đang đuổi Player: **tự phá 2 block cản trước mặt** (trừ Bedrock/Barrier), **tự đặt Cobblestone dưới chân** để leo lên nếu Player ở cao hơn >=2 block, và **tự bắc cầu Cobblestone** qua hố.<br>• Dưới 99% HP: gọi **3 Mini Warden** (scale `0.5`, tốc độ `0.4`).<br>• Dưới 50% HP: gọi **3 Mini Wither Skeleton** cầm Stone Sword (`Fire Aspect I`, `Knockback III`).<br>• Khi chết (bản thật): sinh **5 Skeleton** với cấp độ bằng **người chơi cao cấp nhất** + cơ hội nâng sao vũ khí kết liễu. |
| **Elder Guardian** | • Tấn công chích điện liên tục siêu nhanh (tầm nhìn thẳng, **20% cơ hội mỗi 2 ticks = 0.1s**): gây full sát thương, kèm **Mù lòa 3s**, **Độc II 3s**, và **giáng sét thật** lên mục tiêu.<br>• **5% cơ hội mỗi lần kích hoạt** triệu hồi thêm **3 Guardian**. |
| **Wither Skeleton** | • Gắn tag Mini Boss.<br>• Khi chết: sinh ra **2 Blaze**. |
| **Ravager** | • Gắn tag Mini Boss.<br>• Đánh trúng Player: gây **Làm chậm II + Mù lòa II 10s**.<br>• Khi chịu sát thương: **10% gọi sét thật** lên kẻ tấn công + triệu hồi **2 Pillager**. |
| **Evoker** | • Gắn tag Mini Boss.<br>• Mỗi 1s khi có mục tiêu: **10% giáng sét** (hiệu ứng sét + `6.0` sát thương) thẳng vào mục tiêu. |
| **Polar Bear (Mini Boss biến thể)** | • Khi **Polar Bear** thường chết: **50%** hóa thân thành **Mini Boss Polar Bear** (`Lv. Player + 5`, kích thước `1.8x`, tốc độ `0.4`, `90` HP).<br>• Đòn đánh của Mini Boss Polar Bear gây thêm sát thương chuẩn cộng thẳng bằng **40% Máu tối đa** của Player. |

### 2.3. Quái thù địch (Hostile Mobs)
| Sinh vật | Cơ chế & Điều chỉnh đã cài đặt |
|---|---|
| **Skeleton** | • Tầm phát hiện (`FOLLOW_RANGE`) mở rộng lên **100 block**, tốc độ `0.3`, sát thương x1.5, cầm Bow roll phẩm chất + enchant scale theo gap level.<br>• Mỗi 7 ticks (`0.35s`) khi có tầm nhìn (<20 block): bắn tên bay cực nhanh (`2.5x`), gắn tag `skeleton_fast`, tự kích hoạt **Tàng hình thật ngay lập tức (ẩn cả giáp) trong 1s** mỗi phát bắn + **Kháng lửa 60s**, và tự nhảy lùi nếu Player lại gần (<8 block).<br>• Tên `skeleton_fast` đánh trúng Player: gây **Yếu đuối 10s**, **đánh bật lùi mạnh**, và **hút 50% sát thương thành máu cho Skeleton**. |
| **Zombie** *(và các lớp con kế thừa `Zombie`: **Husk**, **Drowned**, **Zombie Villager**, **Zombified Piglin**)* | • Zombie người lớn chậm (`0.15`), Zombie con nhanh (`0.35`), sát thương **x2.0**, giáp gốc **10.0**, cầm **Kiếm Sắt** roll sao + enchant.<br>• Đòn đánh vào Player gây thêm sát thương % máu: người lớn cộng **10% máu hiện tại**, Zombie con cộng **3% máu tối đa**.<br>• Zombie người lớn khi chết (không phải minion): tách thành **2 Zombie con**.<br>• *(Lưu ý: `Mini Zombie` do Ender Dragon gọi đánh gây Làm chậm II 5s và không tự tách đôi khi chết)*. |
| **Creeper** | • Tốc độ di chuyển tăng lên **0.35**.<br>• Khi phát nổ: sinh thêm **3 quả TNT** sắp nổ (`40` ticks) xung quanh.<br>• **15% Spider** spawn tự nhiên sẽ có Creeper cưỡi trên lưng. |
| **Spider** *(và **Cave Spider** kế thừa `Spider`)* | • Tốc độ di chuyển tăng lên **0.45**.<br>• **15%** tỉ lệ khi spawn được **Creeper** cưỡi trên lưng.<br>• Đòn đánh trúng Player: đặt **Mạng nhện (`COBWEB`)** ngay chân + gây **Làm chậm II 6s**. |
| **Enderman** | • Ở The End: luôn **chủ động target người chơi** trong bán kính 40 block.<br>• Biết **leo trụ**: đặt **End Stone** dưới chân và dịch chuyển lên nếu mục tiêu cao hơn >=2 block.<br>• Đòn đánh trúng Player: gây **Tối tăm + Mù lòa 10s** (như Warden). |
| **Breeze** | • Tốc độ di chuyển tăng lên **0.5**.<br>• Mỗi 7 ticks khi thấy Player (<20 block): bắn **Wind Charge** thẳng tốc độ cao (`2.0x`).<br>• Đòn đánh trúng Player: gây **Độc II 5s**. |
| **Phantom** | • Tốc độ bay & di chuyển đặt lên **1.5**, mỗi 2 ticks có **20% lao vọt về mục tiêu** (`1.5x`).<br>• Đòn đánh trúng Player: gây **Độc II + Mù lòa II + Làm chậm II trong 10s** + **giáng sét thật**.<br>• `Mini Phantom` do Boss gọi tự liên tục tìm mục tiêu Player. |
| **Blaze** | • Khi đang target Player: mỗi 1s có **10%** đặt **Lava tạm thời** ngay dưới chân Player (tự xóa sau 3 giây).<br>• Khi chết (bản thường): **30%** sinh ra **2 Mini Blaze**. |
| **Ghast** | • Cầu lửa bắn ra tăng **x2 tốc độ**.<br>• Khi cầu lửa nổ/va chạm: sinh ra ngay **1 Spider cõng Creeper** tại điểm nổ. |
| **Slime & Magma Cube** | • Khi chết: sinh ra **1 Zombie** tại vị trí chết. |
| **Guardian** *(bản thường)* | • Bơi cực nhanh (`MOVEMENT_SPEED = 0.7`).<br>• Trong tầm nhìn: **33% cơ hội mỗi 2 ticks (`0.1s`)** gây thêm 1 đòn sát thương liên tục. |
| **Pillager** | • Trong tầm nhìn: **10% cơ hội mỗi 2 ticks (`0.1s`)** bắn thêm mũi tên thẳng tốc độ `2.0x`. |
| **Shulker** | • Trong tầm nhìn: **20% cơ hội mỗi 2 ticks (`0.1s`)** bắn đạn Shulker bay thẳng tốc độ `2.5x`. |
| **Vindicator** | • Tốc độ di chuyển tăng lên **0.42**.<br>• Đòn đánh trúng Player: đốt cháy **10s** + **Làm chậm II 10s**. |
| **Vex** | • Đòn đánh trúng Player: gây **Tối tăm + Mù lòa 10s**. |
| **Witch** | • Khi máu tụt xuống dưới **10%**: triệu hồi **1 Creeper có Speed II**. |
| **Piglin & Zombified Piglin (`PigZombie`)** | • Khi ở **Nether**, máu tụt dưới **30%** và có Player trong bán kính 10 block: đặt **giường đỏ kích nổ** (`5.0f`) + sinh thêm **1 TNT**. |

### 2.4. NPC, Golem & Động vật có cơ chế riêng
| Sinh vật | Cơ chế & Điều chỉnh đã cài đặt |
|---|---|
| **Villager** | • Toàn bộ hệ thống sinh tồn, tự vệ theo nghề, gọi Vệ Binh Làng, trade theo level và cơ chế trêu chọc `+70%` tốc chạy + choáng (chi tiết ở [Mục 6](#6-hệ-thống-dân-làng)). |
| **Iron Golem** | • **Bản hoang dã / Vệ Binh Làng**: ném **Ender Pearl** áp sát khi Player đứng cao hơn >=3 block (`20%/s`).<br>• **Tất cả Iron Golem khi tấn công**: tốc đánh nhanh hơn (`20%` bonus hit mỗi 2 ticks = `0.1s`), mỗi đòn tăng **+1% Max HP & +1% Damage** cho bản thân và **hút 70% sát thương thành máu**.<br>• **Pet (`SummonMiniGolem`)**: scale `0.5`, cấp `Lv.Player - 2`, tự teleport theo chủ khi cách >13 block, không đánh chủ/Villager.<br>• **Vệ Binh Làng (`village_guard`)**: không rơi sắt/hoa khi chết, tự biến mất sau 90s. |
| **Snow Golem (`Snowman`)** | • Cộng thẳng **+200 HP**, đi nhanh **x2**, tự tìm đánh `Monster` trong bán kính 20 block.<br>• Bắn cầu tuyết tốc độ `3.0x` mỗi 2 ticks (`0.1s`), giữ **Kháng lửa + Hồi phục II**, **20% đặt Powder Snow** dưới chân mục tiêu, tự bắc cầu **Ice** và leo đất **Dirt**.<br>• Cầu tuyết gây `5.0 + 0.04% MaxHP` + cháy/khô héo/độc/chậm 10s, **hút máu 100%**, và **cướp 1% MaxHP + 1% Damage** của `Monster` cộng vào bản thân.<br>• Bị trúng cầu tuyết sẽ không mất máu mà **hồi 20 HP**; khi chết **50%** sinh thêm 1 Snow Golem mới. |
| **Cow** | • Chịu quy tắc phản công theo level (`Animals`).<br>• Khi bị vắt sữa bằng **Bucket**: đá văng sinh vật gần nhất trong 5 block (`5.0` damage + knockback `1.2x`). |
| **Sheep** | • Chịu quy tắc phản công theo level (`Animals`).<br>• Khi bị cạo lông bằng kéo: nổi điên (`angry_sheep`), đuổi đánh sinh vật gần nhất trong 15 block (`5.0` damage) cho tới khi mọc lại lông. |
| **Panda** | • Chịu quy tắc phản công theo level (`Animals`).<br>• Khi chết: sinh ra **3 Creeper**. |

---

## 3. Danh sách Quái & Sinh vật CHƯA ĐƯỢC CHỈNH CƠ CHẾ RIÊNG

Các sinh vật dưới đây hiện **chưa có kỹ năng/hành vi riêng biệt**, nhưng **vẫn chịu các quy tắc chung**:
- Được gắn `[Lv. X]`, tăng `+20% HP/cấp`, `+10% Dmg/cấp`, `+0.2% Speed/cấp`.
- Nếu là `Monster`: vẫn hưởng 50% mặc giáp sao/enchant, 30% cầm 1–10 Totem, và không đánh nhau.
- Nếu là `Animals`: vẫn tự phản công khi `Level >= Level Player - 2`.

| Nhóm | Sinh vật chưa có cơ chế riêng |
|---|---|
| **Quái thù địch (`Monster` / Hostile)** | • **Stray**, **Bogged** *(và biến thể skeleton mới như Parched)* — chưa có handler riêng vì không kế thừa `Skeleton` trong Bukkit.<br>• **Piglin Brute**, **Hoglin**, **Zoglin**.<br>• **Silverfish**, **Endermite**.<br>• **Illusioner**, **Creaking**.<br>• *(Lưu ý: `Husk`, `Drowned`, `Zombie Villager` hiện dùng chung hành vi của `Zombie`; `Cave Spider` dùng chung hành vi của `Spider` do kế thừa class, chưa có skill tách riêng theo từng loài).* |
| **NPC phụ & Quái trung lập / Bị động** | • **Wandering Trader**, **Trader Llama**.<br>• **Wolf**, **Bee**, **Goat**, **Fox**, **Dolphin**, **Llama**, **Polar Bear** *(lúc còn sống bình thường chưa có skill riêng — chỉ biến thành Mini Boss khi chết)*, **Mooshroom**.<br>• **Pig**, **Chicken**, **Rabbit**, **Horse**, **Donkey**, **Mule**, **Camel**, **Turtle**, **Frog**, **Axolotl**, **Sniffer**, **Armadillo**, **Allay**, **Cat**, **Ocelot**, **Parrot**, **Bat**, **Squid**, **Glow Squid**, **Strider**, **Cod / Salmon / Pufferfish / Tropical Fish / Tadpole**, **Nautilus / Happy Ghast / Copper Golem** *(nếu xuất hiện trên bản 1.21.11)*. |

---

## 4. Hệ thống RPG Người Chơi

### 4.1. Cấp độ & Kinh nghiệm (`PlayerDataManager` & `PlayerListener`)
- **Level không giới hạn trần**, XP cần cho mỗi cấp: `100 * 1.1^(Level - 1)`.
- **Điểm tiềm năng mỗi khi lên cấp**: `5 + (Level / 5)` điểm.
- **Nguồn XP (chỉ tính khi tự tay cày, không tính Creative/Spectator)**:
  - **Đập block tự nhiên** *(có bộ nhớ LRU theo dõi 4096 block do người chơi đặt để chặn exploit đặt-đập farm XP)*: Quặng hiếm (Kim cương, Ngọc lục bảo, Ancient Debris) `15 XP`; Quặng trung (Vàng, Redstone, Lapis, Quartz) `8 XP`; Quặng thường (Sắt, Đồng, Than) `5 XP`; Nông sản chín (Lúa mì, Cà rốt, Khoai tây, Củ dền, Nether Wart, Ca cao, Dưa/Bí) `6 XP`; Gỗ `3 XP`; Block tự nhiên khác `1 XP`. Nhân thêm với hệ số **Mining XP** và **ExpBoost (+50%)**.
  - **Tiêu diệt sinh vật**: `(MobLv * 8) + (MaxHP * 0.5)` (tối thiểu `5 XP`); nhân **x3** cho Mini Boss và **x8** cho Boss (`Wither`, `EnderDragon`, `Warden` bản thật).
- **Action Bar**: Hiển thị liên tục mỗi giây: `❤ HP/MaxHP  ⭐ Lv.X (XP%)  ⚡ Trạng thái hồi chiêu 3 kỹ năng`.

### 4.2. 8 Chỉ số nhân vật (`PlayerData`)
| Chỉ số | Lợi ích chi tiết |
|---|---|
| **Sinh Lực (`Max Health`)** | `+3.0 Max HP`/điểm + thưởng thêm `+10.0 Max HP` mỗi mốc 25 điểm (thanh máu giữ gọn 10 tim visually, máu thật xem trên Action Bar). |
| **Sức Mạnh (`Strength`)** | `+1.0` sát thương cộng thẳng và `+1.5%` nhân sát thương mỗi điểm (tối đa **x4.0**, áp dụng cho **cả cận chiến lẫn cung/nỏ/đinh ba**). |
| **Phòng Thủ (`Defense`)** | Tăng Armor (`+0.5`/điểm, max 20), Armor Toughness (`+0.4`/điểm, max 20), Kháng đẩy lùi (`+1%`/điểm, max 80%), và **Giảm sát thương RPG riêng tới 75%** (`Defense / (Defense + 60)`) vượt trần giáp vanilla. |
| **Chí Mạng & Xuyên Giáp (`Crit`)** | Tỉ lệ chí mạng khởi điểm `5% + 0.6%`/điểm (max `65%`), sát thương chí mạng `x1.5` tăng thêm `+1.5%`/điểm (max `x3.5`), và xuyên giáp mục tiêu có giáp lên tới `50%`. |
| **Hồi Phục (`Vitality`)** | Tự hồi máu mỗi 2 giây (`0.5 + 0.25*pt + %MaxHP`), tăng hiệu quả hồi máu/hút máu tới **x2.0**, và giảm thời gian hiệu ứng bất lợi tới **50%** (`Poison`, `Wither`, `Slowness`, `Blindness`, `Darkness`, `Weakness`, `Nausea`). |
| **Nhanh Nhẹn (`Agility`)** | Tăng tốc độ chạy (max `0.65`, có thể khóa về `0.2` khi build), tăng tốc độ đánh (`+0.04`/điểm, max `+4.0`), và tăng **Né tránh hoàn toàn đòn đánh** lên tới **30%** (`Speed / (Speed + 180)`). |
| **Tốc Độ Đào (`Mining`)** | `+20%` tốc độ phá block (`PLAYER_BLOCK_BREAK_SPEED` hoặc Haste fallback) mỗi điểm, và `+5%` XP đào/farm mỗi 10 điểm. |
| **Bền Bỉ & Hồi Chiêu (`Stamina`)** | `+5%` tỉ lệ không tụt thức ăn mỗi điểm (max `90%`) và **giảm thời gian hồi chiêu kỹ năng** lên tới **45%**. |

### 4.3. 3 Kỹ năng chủ động (`PlayerSkillListener`)
*(Thời gian hồi chiêu giảm theo chỉ số Stamina/Hunger, có thể bật/tắt trong GUI `/rpg`)*

| Kỹ năng | Phím kích hoạt | Hiệu ứng & Hồi chiêu gốc |
|---|---|---|
| **Dash (Né Đòn)** | `Shift + F` *(đổi tay)* | Lướt nhanh theo hướng nhìn, tự phá `COBWEB` quanh người, miễn nhiễm sát thương **0.5 giây**, nhận Speed II 2s. **CD: 8s**. |
| **War Cry (Bộc Phá)** | `Shift + Chuột phải` khi cầm **Vũ khí** *(Kiếm/Rìu/Giáo/Chùy/Đinh ba)* | Sóng xung kích bán kính 6 block gây `10 + FlatDmg*1.5` và hất văng `Monster` (không hại Dân làng/Pet), xóa sạch mọi hiệu ứng bất lợi, nhận Strength I (6s) + Absorption (`15% Max HP`, tối đa 20). **CD: 20s**. |
| **Second Wind (Hồi Phục Khẩn)** | `Shift + Chuột phải` với **Tay không / Khiên** *(hoặc **tự kích hoạt** khi HP rơi dưới 20%)* | Hồi ngay `25%–50% Max HP` (scale theo Vitality & Healing Multiplier), dập lửa, nhận Resistance II trong 4s. **CD: 45s**. |

### 4.4. Giao diện `/rpg` (`StatsGUI` 54 slot)
- Hỗ trợ cộng nhanh `+1` (Chuột trái), `+10` (Chuột phải), `+100` (Shift+Trái), `+Tất cả` (Shift+Phải).
- **3 nút điều khiển linh hoạt ở hàng dưới**:
  - **Khóa tốc độ chạy** (Slot 47): Giữ tốc chạy mặc định khi xây nhà mà vẫn giữ nguyên Né tránh & Tốc đánh.
  - **Tẩy điểm / Respec** (Slot 49, `Shift + Click`): Hoàn trả 100% toàn bộ điểm đã cộng về điểm tiềm năng (cooldown 60s).
  - **Bật/Tắt Kỹ năng chủ động** (Slot 51): Bật hoặc tắt nhanh 3 kỹ năng chủ động.

---

## 5. Trang bị, Phẩm chất, Đe & Hiệu ứng

### 5.1. Bảng phẩm chất vật phẩm (`ItemQuality`)
Áp dụng tự động khi **chế tạo**, **lượm từ rương/mob rơi**, **nhận/click trong túi đồ**, hoặc **giao dịch Dân Làng**:

| Phẩm chất | Tỉ lệ roll | Chỉ số cộng thêm | Số hiệu ứng | Độ bền |
|---|---|---|---|---|
| **0 Sao** | 40% | **-30%** (bị trừ sát thương/giáp) | 0 | Thường |
| **1 Sao** | 30% | **+0%** (100% gốc) | 0 | Thường |
| **2 Sao** | 15% | **+30%** | 0 | Thường |
| **3 Sao** | 7% | **+60%** | 0 | Thường |
| **4 Sao** | 5% | **+60%** | 1 Thường | Thường |
| **5 Sao** | 2% | **+100%** | 2 Thường | Thường |
| **6 Sao** | 0.5% | **+120%** | 2 Thường | **x2 độ bền** |
| **7 Sao** | 0.4% | **+150%** | 3 Cao cấp | **x4 độ bền** |
| **8 Sao** | 0.09% | **+200%** | 4 Cao cấp | **Không thể phá hủy** |
| **9 Sao** | 0.009% | **+400%** | 1 Thần thoại + 4 Cao cấp | **Không thể phá hủy** |
| **10 Sao** | 0.001% | **+1000%** | 2 Thần thoại + 8 Cao cấp | **Không thể phá hủy** |
| **11 Sao** | Chỉ qua nâng cấp | **+2000%** | 3 Thần thoại + 12 Cao cấp | **Không thể phá hủy** |

- **Nâng sao khi hạ Boss**: Giết `Warden`, `Wither`, `EnderDragon`, hoặc `ElderGuardian` bằng vũ khí có sao có tỉ lệ nâng thêm **+1 sao** (`0–6 sao`: 100%, `7 sao`: 50%, `8 sao`: 25%, `9 sao`: 5%, `10 sao`: 1%).

### 5.2. Toàn bộ hiệu ứng vật phẩm đã hoạt động
- **Vũ khí cận chiến & Tầm xa (Cung / Nỏ / Đinh ba)**:
  - *Thường*: `Burn`, `Slow`, `Poison`, `Freeze`, `Blindness`, `Wither`, `Weakness`, `Venom`.
  - *Cao cấp*: `Lifesteal` (hút 20% dmg), `TrueDamage` (5% MaxHP chuẩn), `ArmorPenetration` (x1.5 dmg), `MaxHPPoison` (2% MaxHP chuẩn), `Vampire` (hút 50% dmg), `Crit` (20% x2 dmg), `Bleed` (+30% dmg chuẩn), `ChainLightning` (15% giật sét lan), `IceSpikes` (làm chậm IV + 5 chuẩn).
  - *Thần thoại*: `SummonMiniGolem` (tối đa 3 con, `Lv.Player-2`), `SummonMiniWarden` (tối đa 3 con, `Lv.Player-2`), `Flight` (bay khi giữ trong túi), `Lightning`, `KnockbackBurst`, `Execute` (+50% dmg khi mục tiêu <=30% HP), `SoulEater` (hồi 10% MaxHP + thịt + Strength I khi kết liễu), `TimeStop`, `MeteorStrike`.
- **Giáp & Khiên & Elytra**:
  - *Thường*: `Resilience` (-5% dmg/món), `FireImmunity` (25% dập lửa), `Regeneration`, `Aegis` (-20% dmg khi <50% HP), `Fortitude` (tạo tim vàng), `Featherweight` (Speed II), `WaterBreathing`, `JumpBoost`.
  - *Cao cấp*: `Thorns` (phản 10% dmg), `Dodge` (5% né/món), `ExplosionShield` (-30% nổ), `Spikes` (20% gai lan 3 block), `Grounded` (chống đẩy lùi).
  - *Thần thoại*: `LastStand` (Resistance III khi <=20% HP), `Retribution` (nổ khi chết), `Immortality` (5% sống sót khi chí tử), `Ghost` (tàng hình thật 5s + Speed II), `Forcefield` (15% hất văng kẻ đánh), `Phoenix` (hồi sinh đầy máu, CD 5 phút), `WorldTreeBlessing` (khiên đỡ đòn ban buff Táo Vàng Enchant + đầy thức ăn).
- **Công cụ (`ToolEffectListener` — đủ 13 hiệu ứng)**:
  - *Thường*: `AutoSmelt` (tự nung quặng/cát/gỗ/đá, tôn trọng Fortune), `ExpBoost` (+50% XP vanilla & RPG), `NightVision` (nhìn đêm khi trang bị/cầm), `Lightweight` (Haste II khi cầm), `AutoReplant` (tự trồng lại nông sản chín), `Magnetic` (hút thẳng đồ đào vào túi).
  - *Cao cấp*: `VeinMiner` (đào cả mạch tối đa 16 quặng), `Timber` (chặt cả cây tối đa 24 khúc gỗ), `Excavator` (đào 3x3 đất/đá, tối đa +8 block), `TreasureHunter` (8% rơi sắt/vàng/ngọc/kim cương/XP), `Haste` (Haste II khi cầm).
  - *Thần thoại*: `SpawnerSilkTouch` (đào giữ nguyên loại mob trong Spawner, không drop XP exploit), `BlackHole` (hút item & XP rơi trong 12 block), `MidasTouch` (10% rơi Gold Nugget, 1% Gold Ingot), `TimeWarp` (Haste IV + Speed II 4s khi đào), `StarCatcher` (2% thưởng lớn RPG XP + 0.3% Táo Vàng Phù Phép).

### 5.3. Đe không giới hạn, Stack 99 & Hiệu ứng đặc biệt
- **Đe (`EnchantListener`)**: Ghép sách hoặc trang bị cùng cấp để cộng dồn vượt cấp vanilla (ví dụ Sharpness 10+), giữ nguyên tên đổi, bỏ giới hạn `"Too Expensive!"` (chi phí cố định 5 level), giới hạn an toàn cho các chỉ số % dễ bất tử (`Protection` nhóm tối đa 20, `Thorns`/`FeatherFalling`/`Knockback`/`Punch` tối đa 10, `Looting`/`Fortune` tối đa 20).
- **Stack 99 (`InventoryListener` & `StackManager`)**: Mọi vật phẩm **không có độ bền** (tài nguyên, khối, mũi tên, thức ăn, totem...) tự động gộp và nâng stack tối đa lên **99**. Trang bị có độ bền giữ stack `1` để tránh lỗi data component 1.21+.
- **Bay Lượn (`rpg_flight`) & Vô Hạn (`rpg_infinite`)**: Tự phục hồi stack `99` cho vật phẩm tiêu hao/đặt block (tách stack hoặc vứt <99 sẽ mất Vô Hạn để tránh dupe), hoặc Unbreakable cho trang bị; Totem God / Totem Vô Hạn nổ xong tự hồi lại đúng tay (chính hoặc phụ) + tặng 1 Enchanted Golden Apple.

---

## 6. Hệ thống Dân Làng

### 6.1. Cân bằng sức mạnh & Tự vệ toàn map (`VillagerCombatManager`)
- **Scale chỉ số riêng**: Máu tối đa `60 * (1 + Lv * 0.25)`, Giáp `8 + Lv * 0.3` (max 20), Armor Toughness `4 + Lv * 0.15` (max 12), Kháng đẩy lùi `0.45`.
- **Giảm sốc sát thương**: Mọi sát thương từ `Monster`, nổ, sét, cháy, lava, độc, khô héo lên Dân Làng được **giảm 50%** và **chặn trần tối đa 20% Max HP mỗi đòn**.
- **Tự hồi phục**: Hồi `4% Max HP` mỗi 2s khi rảnh (`2%` khi đang đánh nhau) và rút ngắn thời gian hiệu ứng Độc / Khô héo.
- **Đánh trả theo nghề nghiệp**: Khi bị `Monster` tấn công hoặc thấy quái trong 10 block, tối đa 5 dân làng trưởng thành trong 16 block cùng lao vào phản công:
  - **Cleric**: Hồi `15% Max HP` cho Dân Làng & Iron Golem trong 12 block mỗi 4s + giáng sét gây `7 + Lv*1.4` sát thương.
  - **Fletcher / Librarian**: Bắn tên từ xa (tới 18 block), gây `7 + Lv*1.4` sát thương (**+50%** lên Phantom & Creeper).
  - **Weaponsmith / Armorer / Toolsmith / Butcher**: Cận chiến mạnh `(6 + Lv*1.4) * 1.35` + tự buff Resistance I + đẩy lùi.
  - **Các nghề khác**: Đánh cận chiến `6 + Lv*1.4` kèm Slowness II + Weakness.
- **Gọi Vệ Binh Làng**: Nếu Dân Làng tụt dưới **50% HP** khi đánh nhau với quái và vùng 4x4 chunk có <2 Iron Golem, triệu hồi **Vệ Binh Làng** (`Lv = max(VillagerLv, HighestPlayerLv)`, cooldown vùng 120s, tự biến mất sau 90s, không rơi sắt).
- **Giao dịch nâng cao (`VillagerListener`)**: Bán vũ khí, công cụ, giáp, cung/nỏ/khiên và **Sách phù phép** được roll sao phẩm chất và scale cấp độ phù phép theo cấp Dân Làng.

### 6.2. Cơ chế vui nhộn khi người chơi đánh Dân Làng
1. Khi người chơi đánh một con Dân Làng (cận chiến hoặc bắn cung): có **50% tỉ lệ** con Dân Làng đó sẽ không chết ở đòn cảnh cáo (giữ tối thiểu `max(10, 30% MaxHP)`) và hét lên:
   > `§e§l[Dân Làng] "Ngon thì đánh tao thêm cái nữa!"`
2. Nếu người chơi đánh thêm cái nữa trong vòng 60s: Dân Làng lập tức hô:
   > `§c§l[Dân Làng] "Nói không nghe! Đứng lại đó cho tao!"`
3. Ngay lập tức con Dân Làng đó sẽ:
   - **Ghim đuổi đánh** riêng người chơi đó trong 60s (tự làm mới nếu đánh tiếp).
   - Nhận **+70% tốc độ di chuyển** (transient `AttributeModifier`, tự gỡ sạch khi nguôi hoặc restart).
   - Mỗi cú đấm gây `6 + Lv*1.2` sát thương kèm **Choáng & Chóng mặt**: `NAUSEA II` (6s), `SLOWNESS IV` (2s), `DARKNESS` (1.5s), `WEAKNESS` (3s) + hất nhẹ.

---

## 7. Sự kiện ngẫu nhiên & Danh sách Lệnh

- **Sự kiện ngẫu nhiên (`RandomEventManager`)**: Mỗi **20 phút** (`24000` ticks) roll:
  - **40%** *Skeleton Invasion* (spawn 15 Skeleton quanh mỗi người chơi).
  - **30%** *Zombie Invasion* (spawn 15 Zombie quanh mỗi người chơi).
  - **30%** Không có sự kiện.
- **Lệnh trong game**:
  - `/rpg` — Mở GUI nâng cấp 8 chỉ số RPG, Tẩy điểm, Khóa tốc chạy, Bật/Tắt kỹ năng.
  - `/adminrpg totem` *(quyền `rpg.admin`)* — Nhận Totem God bất tử.
  - `/adminrpg resetlevel <player>` *(quyền `rpg.admin`)* — Reset cấp & toàn bộ 8 chỉ số về 1.
  - `/adminrpg addlevel <player> <amount>` *(quyền `rpg.admin`)* — Cộng/trừ cấp độ cho người chơi.
  - `/adminrpg addstar` *(quyền `rpg.admin`)* — Nâng cấp vật phẩm trên tay lên +1 sao (tối đa 11 sao).
  - `/rpgfly` *(quyền `rpg.admin`)* — Ép hiệu ứng Bay Lượn vào vật phẩm trên tay.
  - `/rpginf` *(quyền `rpg.admin`)* — Ép hiệu ứng Vô Hạn (99 stack / Unbreakable) vào vật phẩm trên tay.
