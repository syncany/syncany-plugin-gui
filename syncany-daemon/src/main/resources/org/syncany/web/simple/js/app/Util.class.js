function htmlEncode(html) {
	return document.createElement('a')
		.appendChild(document.createTextNode(html)).parentNode.innerHTML;
}

function basename(path) {
	return path.split(/[\\/]/).pop();
}

function formatFileSize(bytes) {
    var thresh = 1024;
    var units = ['kB','MB','GB','TB','PB','EB','ZB','YB'];

    if(bytes < thresh) {
    	return bytes + ' B';
    }
    
    var u = -1;
    do {
        bytes /= thresh;
        ++u;
    } while(bytes >= thresh);
    
    return bytes.toFixed(1)+' '+units[u];
}

function toFileVersions(xml) {
	var fileElements = xml.find('files > file');
	var fileVersions = [];
		
	$(fileElements).each(function (i, file) {
		var fileXml = $(file);

		fileVersions.push(new ExtendedFileVersion(
			fileXml.find('fileHistoryId').text(),		
			fileXml.find('version').text(),
			fileXml.find('path').text(),
			fileXml.find('type').text(),
			fileXml.find('status').text(),
			fileXml.find('size').text(),
			fileXml.find('lastModified').text(),
			fileXml.find('checksum').text(), // TODO fix checksum formatting
			fileXml.find('updated').text(),
			fileXml.find('posixPermissions').text(),
			"" // TODO fix DOS attrs
		));
	});
	
	console.log(fileVersions);
	return fileVersions;
}
