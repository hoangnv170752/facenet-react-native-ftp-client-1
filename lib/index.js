import { NativeModules, NativeEventEmitter } from 'react-native';
import { get } from 'react-native/Libraries/TurboModule/TurboModuleRegistry';
const { RNFtpClient } = NativeModules;
const RNFtpClientEventEmitter = new NativeEventEmitter(RNFtpClient);
export var FtpFileType;
(function (FtpFileType) {
    FtpFileType["Dir"] = "dir";
    FtpFileType["File"] = "file";
    FtpFileType["Link"] = "link";
    FtpFileType["Unknown"] = "unknown";
})(FtpFileType || (FtpFileType = {}));
;
;
;
var FtpClient;
(function (FtpClient) {
    function getEnumFromString(typeString) {
        switch (typeString) {
            case "dir":
                return FtpFileType.Dir;
            case "link":
                return FtpFileType.Link;
            case "file":
                return FtpFileType.File;
            case "unknown":
            default:
                return FtpFileType.Unknown;
        }
    }
    function setup(config) {
        RNFtpClient.setup(config.ip_address, config.port, config.username, config.password);
    }
    FtpClient.setup = setup;
    async function list(remote_path) {
        const files = await RNFtpClient.list(remote_path);
        return files.map((f) => {
            return {
                name: f.name,
                type: getEnumFromString(f.type),
                size: +f.size,
                timestamp: new Date(f.timestamp)
            };
        });
    }
    FtpClient.list = list;
    async function uploadFile(local_path, remote_path) {
        return RNFtpClient.uploadFile(local_path, remote_path);
    }
    FtpClient.uploadFile = uploadFile;
    async function cancelUploadFile(token) {
        return RNFtpClient.cancelUploadFile(token);
    }
    FtpClient.cancelUploadFile = cancelUploadFile;
    function addProgressListener(listener) {
        return RNFtpClientEventEmitter.addListener("Progress", listener);
    }
    FtpClient.addProgressListener = addProgressListener;
    async function remove(remote_path) {
        return RNFtpClient.remove(remote_path);
    }
    FtpClient.remove = remove;
    async function moveFileOrDirectory(remote_path, new_remote_path) {
        return RNFtpClient.moveFileOrDirectory(remote_path, new_remote_path);
    }
    FtpClient.moveFileOrDirectory = moveFileOrDirectory;
    async function makeDir(remote_path) {
        return RNFtpClient.makeDir(remote_path);
    }
    FtpClient.makeDir = makeDir;
    FtpClient.ERROR_MESSAGE_CANCELLED = RNFtpClient.ERROR_MESSAGE_CANCELLED;
    async function downloadFile(local_path, remote_path) {
        return RNFtpClient.downloadFile(local_path, remote_path);
    }
    FtpClient.downloadFile = downloadFile;
    async function cancelDownloadFile(token) {
        return RNFtpClient.cancelDownloadFile(token);
    }
    FtpClient.cancelDownloadFile = cancelDownloadFile;
    async function checkFileExists(remote_path, remote_file_name) {
        return RNFtpClient.checkFileExists(remote_path, remote_file_name);
    }
    FtpClient.checkFileExists = checkFileExists;
    async function getFolderSize(remote_path) {
        return RNFtpClient.getFolderSize(remote_path);
    }
    FtpClient.getFolderSize = getFolderSize;
})(FtpClient || (FtpClient = {}));
;
export default FtpClient;
