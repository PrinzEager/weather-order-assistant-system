'use strict';

/**
 * Demo 專用 CRUD 共用介面。
 * 尚未提供 Controller/Request DTO 的模組，只做可配置的端點與 JSON 編輯器。
 * 不會在載入時修改任何資料；所有寫入都必須使用者確認。
 */
import { byId, makeElement, showMessage, requestJson } from './common.js';

export function createCrudModule({ section, tbodyId, addId, columns, endpoint, fields, title, template, normalize = value => value, readOnly = false }) {
    let initialized = false;
    let loaded = false;
    let records = [];
    let editor;
    function field(row, value) {
        row.append(makeElement('td', value == null ? '--' : String(value)));
    }
    function refreshMessage(message) {
        const body = byId(tbodyId);
        body.replaceChildren();
        const row = makeElement('tr');
        const cell = makeElement('td', message, 'empty-state');
        cell.colSpan = columns + 1;
        row.append(cell);
        body.append(row);
    }
    function init() {
        if (initialized)
            return;
        initialized = true;
        const root = byId(section), add = byId(addId);
        const bar = makeElement('div', null, 'action-bar');
        const reload = makeElement('button', '重新載入', 'secondary-button');
        reload.type = 'button';
        reload.addEventListener('click', () => void load());
        bar.append(reload);
        root.prepend(bar);
        if (add) {
            add.disabled = readOnly;
            add.addEventListener('click', () => edit(null));
        }
        editor = makeElement('div', null, 'panel');
        editor.hidden = true;
        const h = makeElement('h4', `${title}：進階資料輸入`);
        const note = makeElement('p', '這是進階編輯功能，請先確認資料內容正確，再執行新增或修改；儲存後會更新系統紀錄。');
        const text = makeElement('textarea');
        text.rows = 12;
        text.style.width = '100%';
        text.id = `${section}-json`;
        const buttons = makeElement('div', null, 'action-bar');
        const save = makeElement('button', '確認送出', 'primary-button');
        save.type = 'button';
        const cancel = makeElement('button', '取消', 'secondary-button');
        cancel.type = 'button';
        cancel.addEventListener('click', () => editor.hidden = true);
        buttons.append(cancel, save);
        editor.append(h, note, text, buttons);
        root.append(editor);
        let edited = null;
        function edit(record) {
            edited = record;
            h.textContent = `${record ? '修改' : '新增'}${title}`;
            text.value = JSON.stringify(record ? normalize(record) : template, null, 2);
            editor.hidden = false;
            editor.scrollIntoView({ behavior: 'smooth' });
        }
        save.addEventListener('click', async () => {
            let body;
            try {
                body = JSON.parse(text.value);
                if (!body || Array.isArray(body) || typeof body !== 'object')
                    throw new Error('請輸入正確的資料內容');
            }
            catch (e) {
                showMessage(`輸入內容格式有誤：${e.message}`, 'error');
                return;
            }
            if (!confirm(`確定要${edited ? '修改' : '新增'}${title}？儲存後會更新系統資料。`))
                return;
            save.disabled = true;
            try {
                await requestJson(edited ? `${endpoint}/${edited.id}` : endpoint, edited ? 'PUT' : 'POST', body);
                editor.hidden = true;
                await load();
                showMessage(`${title}已儲存`, 'success');
            }
            catch (e) {
                showMessage(`${title}儲存失敗：${e.message}`, 'error');
            }
            finally {
                save.disabled = false;
            }
        });
        return { edit };
    }
    let editorApi;
    async function load() {
        refreshMessage('正在讀取資料…');
        try {
            const response = await requestJson(endpoint);
            records = Array.isArray(response) ? response : (Array.isArray(response?.content) ? response.content : null);
            if (!records)
                throw new Error('目前無法顯示資料清單，請稍後重新載入');
            const tbody = byId(tbodyId);
            tbody.replaceChildren();
            if (!records.length) {
                refreshMessage('目前沒有資料');
                return true;
            }
            for (const record of records) {
                const row = makeElement('tr');
                for (const f of fields)
                    field(row, typeof f === 'string' ? record[f] : f(record));
                const act = makeElement('td');
                const editBtn = makeElement('button', '編輯', 'secondary-button');
                editBtn.type = 'button';
                editBtn.disabled = readOnly;
                editBtn.addEventListener('click', () => editorApi.edit(record));
                act.append(editBtn);
                const delBtn = makeElement('button', '刪除', 'danger-button');
                delBtn.type = 'button';
                delBtn.disabled = readOnly;
                delBtn.style.marginLeft = '6px';
                delBtn.addEventListener('click', async () => {
                    if (!confirm(`確定刪除${title} #${record.id}？`))
                        return;
                    delBtn.disabled = true;
                    try {
                        await requestJson(`${endpoint}/${record.id}`, 'DELETE');
                        await load();
                        showMessage('刪除成功', 'success');
                    }
                    catch (e) {
                        showMessage(e.message, 'error');
                        delBtn.disabled = false;
                    }
                });
                act.append(delBtn);
                row.append(act);
                tbody.append(row);
            }
            loaded = true;
            return true;
        }
        catch (e) {
            loaded = false;
            refreshMessage(`讀取失敗：${e.message}`);
            showMessage(e.message, 'error');
            return false;
        }
    }
    return {
        init() {
            editorApi = init();
        },
        enter() {
            if (!loaded)
                void load();
        },
        refresh: load,
        getRecords() {
            return records.slice();
        }
    };
}
