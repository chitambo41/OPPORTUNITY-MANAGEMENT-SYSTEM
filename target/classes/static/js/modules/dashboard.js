/* ==========================================================================
   Dashboards — admin overview & teacher overview.
   ========================================================================== */
(function () {
    Router.register('/dashboard', {
        title: 'Dashboard',
        roles: ['ADMIN', 'TEACHER'],
        async render(el) {
            if (Auth.isAdmin()) return renderAdmin(el);
            return renderTeacher(el);
        },
    });

    async function renderAdmin(el) {
        const d = await API.admin.dashboard();
        const attRows = d.todayAttendance || [];
        el.innerHTML = `
            <div class="cards-grid">
                <div class="stat-card"><div class="stat-label">Active Students</div><div class="stat-value">${d.activeStudents}</div></div>
                <div class="stat-card green"><div class="stat-label">Active Teachers</div><div class="stat-value">${d.activeTeachers}</div></div>
                <div class="stat-card orange"><div class="stat-label">Results Pending Review</div><div class="stat-value">${d.resultsPendingReview}</div></div>
                <div class="stat-card green"><div class="stat-label">Fees Collected (term)</div><div class="stat-value">${UI.money(d.feesCollected)}</div></div>
                <div class="stat-card red"><div class="stat-label">Fees Outstanding</div><div class="stat-value">${UI.money(d.feesOutstanding)}</div></div>
            </div>
            <div class="grid-2">
                <div class="card"><h3>👨\u200d👩\u200d👧 Students per class</h3>
                    ${UI.table({
                        columns: [
                            { label: 'Class', key: 'className' },
                            { label: 'Level', key: 'level' },
                            { label: 'Active students', key: 'count' },
                        ],
                        rows: d.studentsPerClass,
                        empty: 'No classes for the current year yet',
                    })}
                </div>
                <div class="card"><h3>🗓️ Today's attendance per class</h3>
                    ${UI.table({
                        columns: [
                            { label: 'Class', key: 'className' },
                            { label: '✅', key: 'present' },
                            { label: '❌', key: 'absent' },
                            { label: '⏰', key: 'late' },
                            { label: 'Unmarked', key: 'unmarked' },
                        ],
                        rows: attRows,
                        empty: 'No classes to show',
                    })}
                </div>
            </div>`;
    }

    async function renderTeacher(el) {
        const d = await API.teacher.dashboard();
        const attColor = {
            MARKED: 'green', PARTIAL: 'orange', NOT_MARKED: 'red', NOT_A_CLASS_TEACHER: 'gray',
        }[d.attendanceStatusToday] || 'gray';
        const attLabel = {
            MARKED: 'Marked ✔', PARTIAL: 'Partially marked', NOT_MARKED: 'Not marked yet',
            NOT_A_CLASS_TEACHER: 'Not a class teacher',
        }[d.attendanceStatusToday] || d.attendanceStatusToday;

        el.innerHTML = `
            <div class="cards-grid">
                <div class="stat-card"><div class="stat-label">My classes</div><div class="stat-value">${(d.myClasses || []).length}</div></div>
                <div class="stat-card ${attColor}"><div class="stat-label">Attendance today</div>
                    <div class="stat-value" style="font-size:16px">${attLabel}</div></div>
                <div class="stat-card orange"><div class="stat-label">Pending marks</div><div class="stat-value">${(d.pendingMarks || []).length}</div></div>
            </div>
            <div class="grid-2">
                <div class="card"><h3>🏫 My classes</h3>
                    ${UI.table({
                        columns: [
                            { label: 'Class', key: 'className' },
                            { label: 'Level', key: 'level' },
                            { label: 'Role', render: r => UI.badge(r.role === 'CLASS_TEACHER' ? 'CLASS TEACHER' : 'SUBJECT TEACHER', r.role === 'CLASS_TEACHER' ? 'blue' : 'gray') },
                            { label: 'My subjects', render: r => UI.esc((r.subjects || []).join(', ') || '—') },
                        ],
                        rows: d.myClasses,
                        empty: 'No class assignments yet',
                    })}
                </div>
                <div class="card"><h3>📝 Pending marks</h3>
                    ${UI.table({
                        columns: [
                            { label: 'Exam', key: 'examName' },
                            { label: 'Class', key: 'className' },
                            { label: 'Subject', key: 'subjectName' },
                            { label: 'Progress', render: r => `
                                <div class="progress-track" style="min-width:110px">
                                    <div class="progress-fill ${r.missing === 0 ? '' : 'warn'}"
                                        style="width:${r.activeStudents ? Math.round(100 * r.studentsWithMarks / r.activeStudents) : 0}%"></div>
                                </div>
                                <small>${r.studentsWithMarks}/${r.activeStudents}</small>` },
                        ],
                        rows: d.pendingMarks,
                        empty: 'All marks entered — nice work! 🎉',
                    })}
                </div>
            </div>`;
    }
})();
