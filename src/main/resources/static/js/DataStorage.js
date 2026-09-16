// DataStorage.js
document.addEventListener('DOMContentLoaded', () => {
    const storageForm = document.getElementById('storageForm');
    const storageTableBody = document.querySelector('#storageTable tbody');
    const localSpace = document.getElementById('localSpace');
    const cloudSpace = document.getElementById('cloudSpace');
    const taskStatus = document.getElementById('taskStatus');
    const notification = document.getElementById('notification');
    const modal = document.getElementById('previewModal');
    const closeModal = document.querySelector('.close');

    // 模拟存储记录（实际场景中应通过 API 或 WebSocket 获取）
    let storedFiles = [
        { id: 1, name: '文件A.csv', type: '本地存储', size: '2MB', date: '2023-10-01', status: '已存储', content: '示例数据...' },
        { id: 2, name: '文件B.json', type: '云存储', size: '1.5MB', date: '2023-10-02', status: '已存储', content: '示例数据...' },
    ];

    // 初始化存储记录表格
    updateStorageTable();

    // 云存储选项切换
    const cloudStorageCheckbox = document.querySelector('input[name="cloudStorage"]');
    const cloudServiceSelect = document.querySelector('select[name="cloudService"]');
    cloudStorageCheckbox.addEventListener('change', () => {
        cloudServiceSelect.disabled = !cloudStorageCheckbox.checked;
    });

    // 存储表单提交事件
    storageForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const formData = new FormData(storageForm);
        const options = {
            localStorage: formData.get('localStorage') === 'on',
            cloudStorage: formData.get('cloudStorage') === 'on',
            storageFormat: formData.get('storageFormat'),
            cloudService: formData.get('cloudService'),
        };

        if (!options.localStorage && !options.cloudStorage) {
            showNotification('请选择至少一种存储方式', 'error');
            return;
        }

        taskStatus.textContent = '存储中...';
        // 模拟存储过程
        setTimeout(() => {
            const newFile = {
                id: storedFiles.length + 1,
                name: `文件${storedFiles.length + 1}.${options.storageFormat}`,
                type: options.cloudStorage ? '云存储' : '本地存储',
                size: `${(Math.random() * 5 + 1).toFixed(2)}MB`,
                date: new Date().toISOString().split('T')[0],
                status: '已存储',
                content: '存储的示例数据...',
            };

            storedFiles.push(newFile);
            updateStorageTable();
            updateStorageStatus();
            taskStatus.textContent = '存储完成';
            showNotification('数据存储成功', 'success');
        }, 1000);
    });

    // 更新存储记录表格
    function updateStorageTable() {
        storageTableBody.innerHTML = '';
        storedFiles.forEach((file) => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${file.id}</td>
                <td>${file.name}</td>
                <td>${file.type}</td>
                <td>${file.size}</td>
                <td>${file.date}</td>
                <td>${file.status}</td>
                <td><button class="btn preview-btn" data-id="${file.id}">预览</button></td>
            `;
            storageTableBody.appendChild(row);
        });

        // 绑定预览按钮事件
        document.querySelectorAll('.preview-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = btn.dataset.id;
                const file = storedFiles.find(f => f.id == id);
                document.getElementById('previewContent').textContent = file.content;
                modal.style.display = 'block';
            });
        });
    }

    // 更新存储状态
    function updateStorageStatus() {
        const localUsed = storedFiles.filter(f => f.type === '本地存储')
            .reduce((sum, f) => sum + parseFloat(f.size), 0).toFixed(2);
        const cloudUsed = storedFiles.filter(f => f.type === '云存储')
            .reduce((sum, f) => sum + parseFloat(f.size), 0).toFixed(2);

        localSpace.textContent = `${localUsed} GB / 100 GB`;
        cloudSpace.textContent = `${cloudUsed} GB / 50 GB`;
    }

    // 提示信息
    function showNotification(message, type) {
        notification.textContent = message;
        notification.className = `notification ${type}`;
        notification.classList.add('show');
        setTimeout(() => {
            notification.classList.remove('show');
        }, 3000);
    }

    // 弹窗相关逻辑
    closeModal.onclick = () => {
        modal.style.display = 'none';
    };
    window.onclick = (event) => {
        if (event.target === modal) {
            modal.style.display = 'none';
        }
    };
});