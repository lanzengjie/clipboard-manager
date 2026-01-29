# 构建和使用说明 (Build and Usage Instructions)

## 系统要求 (System Requirements)

- Android Studio Arctic Fox (2020.3.1) 或更高版本
- JDK 8 或更高版本
- Android SDK (API 24-34)
- 运行设备：Android 7.0 (API 24) 或更高版本

## 构建步骤 (Build Steps)

### 方法1：使用Android Studio

1. 克隆仓库：
   ```bash
   git clone https://github.com/ElmerDunlop/clipboardManager.git
   cd clipboardManager
   ```

2. 用Android Studio打开项目：
   - 打开Android Studio
   - 选择 "Open an Existing Project"
   - 选择clipboardManager目录
   - 等待Gradle同步完成

3. 构建项目：
   - 在菜单栏选择 Build > Make Project
   - 或者点击工具栏的构建按钮

4. 运行应用：
   - 连接Android设备或启动模拟器
   - 点击运行按钮（绿色三角形）
   - 选择目标设备

### 方法2：使用命令行（需要先安装Gradle Wrapper）

如果没有Gradle Wrapper，先安装：
```bash
# 下载Gradle 8.0
wget https://services.gradle.org/distributions/gradle-8.0-bin.zip
unzip gradle-8.0-bin.zip
export PATH=$PATH:gradle-8.0/bin

# 初始化wrapper
gradle wrapper
```

然后构建：
```bash
# 构建Debug版本
./gradlew assembleDebug

# 安装到连接的设备
./gradlew installDebug

# 构建Release版本（需要签名）
./gradlew assembleRelease
```

## 应用功能说明 (App Features)

### 主要功能

1. **开始监听** - 点击"开始监听"按钮启动剪贴板监听服务
2. **自动保存** - 复制任何文本后，应用会自动保存到本地数据库
3. **查看历史** - 在主界面查看所有已保存的剪贴板历史
4. **复制内容** - 点击任意历史记录，将其重新复制到剪贴板
5. **删除记录** - 长按历史记录可删除单条记录
6. **清空历史** - 点击"清空历史"按钮删除所有记录

### 数据存储

- 所有数据使用Room数据库存储在设备本地
- 数据路径：`/data/data/com.clipboard.manager/databases/clipboard_database`
- 自动去重：相同内容不会重复保存

### 隐私保护

- ✅ 所有数据仅保存在本地，不会上传到服务器
- ✅ 无需网络权限
- ✅ 无需特殊系统权限
- ✅ 开源代码，可自行审查

## 常见问题 (FAQ)

### 1. 应用无法监听剪贴板？
- 确保已点击"开始监听"按钮
- 检查应用是否被系统后台限制
- 在设置中允许应用后台运行

### 2. 历史记录不显示？
- 确保监听服务已启动
- 尝试复制一段新文本测试
- 检查数据库是否正常创建

### 3. 如何卸载？
- 在设置 > 应用中卸载
- 卸载会自动删除所有本地数据

## 技术架构 (Technical Architecture)

```
┌─────────────────────────────────────────┐
│          MainActivity (UI)              │
│  - 显示剪贴板历史                        │
│  - 控制监听服务                          │
└──────────────┬──────────────────────────┘
               │
               ├─────► ClipboardViewModel
               │       - LiveData观察
               │       - 业务逻辑
               │
               ├─────► ClipboardMonitorService
               │       - 监听剪贴板变化
               │       - 后台服务
               │
               └─────► Room Database
                       - ClipboardEntry (实体)
                       - ClipboardDao (DAO)
                       - ClipboardRepository (仓库)
```

## 开发说明 (Development Notes)

### 项目结构
```
com.clipboard.manager/
├── database/          # 数据库相关
├── service/           # 后台服务
├── ui/               # UI和ViewModel
├── adapter/          # RecyclerView适配器
└── MainActivity.kt   # 主Activity
```

### 添加新功能
1. 需要修改数据库结构？更新ClipboardEntry和数据库版本号
2. 需要添加新的UI？在res/layout中创建布局文件
3. 需要新的后台任务？使用Kotlin协程在Repository中实现

## 许可证 (License)

请参考项目根目录的LICENSE文件。
