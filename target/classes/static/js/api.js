/* ==========================================================================
   API layer — fetch wrapper with JWT, JSON error handling, 401 redirect.
   ========================================================================== */
(function () {
    const TOKEN_KEY = 'oes_token';
    const USER_KEY = 'oes_user';

    function getToken() { return localStorage.getItem(TOKEN_KEY); }
    function setToken(t) { t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY); }

    function getUser() {
        try { return JSON.parse(localStorage.getItem(USER_KEY)); } catch (e) { return null; }
    }
    function setUser(u) { u ? localStorage.setItem(USER_KEY, JSON.stringify(u)) : localStorage.removeItem(USER_KEY); }

    function onUnauthorized() {
        clearSession();
        if (!location.pathname.endsWith('/login.html')) location.assign('login.html');
    }

    async function request(method, url, body, options = {}) {
        const headers = { ...(options.headers || {}) };
        const token = getToken();
        if (token) headers['Authorization'] = 'Bearer ' + token;
        if (body !== undefined && body !== null && !(body instanceof FormData)) {
            headers['Content-Type'] = 'application/json';
        }

        let res;
        try {
            res = await fetch(url, {
                method,
                headers,
                body: body === undefined || body === null ? undefined : (body instanceof FormData ? body : JSON.stringify(body)),
            });
        } catch (networkErr) {
            throw { status: 0, message: 'Cannot reach the server. Check your connection.' };
        }

        if (res.status === 401 && !options.noRedirect) {
            onUnauthorized();
            throw { status: 401, message: 'Session expired. Please log in again.' };
        }

        let data = null;
        const text = await res.text();
        if (text) {
            try { data = JSON.parse(text); } catch (e) { data = { message: text }; }
        }

        if (!res.ok) {
            throw {
                status: res.status,
                message: (data && data.message) || ('Request failed (' + res.status + ')'),
                data,
            };
        }
        return data;
    }

    function clearSession() {
        setToken(null);
        setUser(null);
    }

    window.API = {
        get: (url, opts) => request('GET', url, null, opts),
        post: (url, body, opts) => request('POST', url, body, opts),
        put: (url, body, opts) => request('PUT', url, body, opts),
        del: (url, body, opts) => request('DELETE', url, body, opts),

        // auth session helpers
        getToken, setToken, getUser, setUser, clearSession,

        // typed helpers for common endpoints
        admin: {
            years: {
                list: () => request('GET', '/api/admin/years'),
                create: (b) => request('POST', '/api/admin/years', b),
                setCurrent: (b) => request('POST', '/api/admin/years/set-current', b),
                updateTerm: (id, b) => request('PUT', '/api/admin/years/terms/' + id, b),
                context: () => request('GET', '/api/admin/years/current-context'),
            },
            teachers: {
                list: (params) => request('GET', '/api/admin/teachers' + qs(params)),
                create: (b) => request('POST', '/api/admin/teachers', b),
                update: (id, b) => request('PUT', '/api/admin/teachers/' + id, b),
                remove: (id, b) => request('DELETE', '/api/admin/teachers/' + id, b),
            },
            subjects: {
                list: () => request('GET', '/api/admin/subjects'),
                create: (b) => request('POST', '/api/admin/subjects', b),
                update: (id, b) => request('PUT', '/api/admin/subjects/' + id, b),
                remove: (id) => request('DELETE', '/api/admin/subjects/' + id),
            },
            classes: {
                list: (year) => request('GET', '/api/admin/classes' + qs(year ? { year } : null)),
                get: (id) => request('GET', '/api/admin/classes/' + id),
                create: (b) => request('POST', '/api/admin/classes', b),
                assignTeacher: (id, b) => request('POST', '/api/admin/classes/' + id + '/class-teacher', b),
                addSubject: (id, b) => request('POST', '/api/admin/classes/' + id + '/subjects', b),
                removeSubject: (id, csId) => request('DELETE', '/api/admin/classes/' + id + '/subjects/' + csId),
            },
            students: {
                search: (params) => request('GET', '/api/admin/students' + qs(params)),
                get: (id) => request('GET', '/api/admin/students/' + id),
                create: (b) => request('POST', '/api/admin/students', b),
                update: (id, b) => request('PUT', '/api/admin/students/' + id, b),
                move: (id, b) => request('POST', '/api/admin/students/' + id + '/move', b),
                remove: (id, b) => request('DELETE', '/api/admin/students/' + id, b),
            },
            promotion: {
                preview: (toYear) => request('GET', '/api/admin/promotion/preview?toYear=' + toYear),
                run: (toYear) => request('POST', '/api/admin/promotion?toYear=' + toYear),
            },
            attendance: {
                classDay: (params) => request('GET', '/api/admin/attendance' + qs(params)),
                summary: (params) => request('GET', '/api/admin/attendance/summary' + qs(params)),
                report: (params) => request('GET', '/api/admin/attendance/report' + qs(params)),
            },
            exams: {
                list: (params) => request('GET', '/api/admin/exams' + qs(params)),
                create: (b) => request('POST', '/api/admin/exams', b),
                status: (id, b) => request('POST', '/api/admin/exams/' + id + '/status', b),
                results: (id) => request('GET', '/api/admin/exams/' + id + '/results'),
                approve: (id, comment) => request('POST', '/api/admin/exams/' + id + '/approve', { comment }),
                return: (id, comment) => request('POST', '/api/admin/exams/' + id + '/return', { comment }),
                reportCard: (id, studentId) => request('GET', '/api/admin/exams/' + id + '/report-card/' + studentId),
                reportCards: (id) => request('GET', '/api/admin/exams/' + id + '/report-cards'),
            },
            fees: {
                setFee: (b) => request('POST', '/api/admin/fees/structures', b),
                listFees: (termId) => request('GET', '/api/admin/fees/structures?termId=' + termId),
                pay: (b) => request('POST', '/api/admin/fees/payments', b),
                statuses: (params) => request('GET', '/api/admin/fees/statuses' + qs(params)),
                summary: (termId) => request('GET', '/api/admin/fees/summary?termId=' + termId),
                statement: (studentId) => request('GET', '/api/admin/fees/statement/' + studentId),
                receipt: (num) => request('GET', '/api/admin/fees/receipt/' + encodeURIComponent(num)),
            },
            dashboard: () => request('GET', '/api/admin/dashboard'),
        },
        teacher: {
            dashboard: () => request('GET', '/api/teacher/dashboard'),
            myClasses: () => request('GET', '/api/teacher/my-classes'),
            classDetail: (id) => request('GET', '/api/teacher/my-classes/' + id),
            attendanceRoster: (params) => request('GET', '/api/teacher/attendance' + qs(params)),
            saveAttendance: (b) => request('POST', '/api/teacher/attendance', b),
            myExams: (termId) => request('GET', '/api/teacher/exams' + (termId ? '?termId=' + termId : '')),
            saveMarks: (b) => request('POST', '/api/teacher/marks', b),
            progress: (examId) => request('GET', '/api/teacher/exams/' + examId + '/progress'),
            results: (examId) => request('GET', '/api/teacher/exams/' + examId + '/results'),
            sendResults: (examId, comment) => request('POST', '/api/teacher/exams/' + examId + '/send', { teacherComment: comment }),
        },
    };

    function qs(params) {
        if (!params) return '';
        const usp = new URLSearchParams();
        Object.keys(params).forEach(k => {
            if (params[k] !== undefined && params[k] !== null && params[k] !== '') usp.append(k, params[k]);
        });
        const s = usp.toString();
        return s ? '?' + s : '';
    }
})();
