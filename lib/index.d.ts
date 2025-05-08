import { EmitterSubscription } from 'react-native';
import { moveFile } from 'react-native-fs';
import { move } from 'react-native-redash';
export declare const enum FtpFileType {
    Dir = "dir",
    File = "file",
    Link = "link",
    Unknown = "unknown"
}
export interface ListItem {
    name: string;
    type: FtpFileType;
    size: number;
    timestamp: Date;
}
export interface FtpSetupConfiguration {
    ip_address: string;
    port: number;
    username: string;
    password: string;
}
declare module FtpClient {
    function setup(config: FtpSetupConfiguration): void;
    function list(remote_path: string): Promise<Array<ListItem>>;
    function uploadFile(local_path: string, remote_path: string): Promise<void>;
    function cancelUploadFile(token: string): Promise<void>;
    function addProgressListener(listener: (data: {
        token: string;
        percentage: number;
    }) => void): EmitterSubscription;
    function remove(remote_path: string): Promise<void>;
    const ERROR_MESSAGE_CANCELLED: string;
    function downloadFile(local_path: string, remote_path: string): Promise<void>;
    function cancelDownloadFile(token: string): Promise<void>;
    function makeDir(remote_path: string): Promise<void>;
    function moveFileOrDirectory(remote_path: string, new_remote_path: string): Promise<void>;
    function checkFileExists(remote_path: string, remove_file_name: string): Promise<boolean>;
    function getFolderSize(remote_path: string): Promise<number>;
}
export default FtpClient;
