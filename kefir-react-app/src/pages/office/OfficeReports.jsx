// src/pages/office/OfficeReports.jsx
import React, { useState, useEffect } from 'react';
import OfficeSidebar from '../../components/office/OfficeSidebar';

const OfficeReports = () => {
  const [reports, setReports] = useState(null);
  const [loading, setLoading] = useState(true);
  const [dateRange, setDateRange] = useState('week');

  useEffect(() => {
    fetchReports();
  }, [dateRange]);

  const fetchReports = async () => {
    try {
      setLoading(true);
      // Заглушка для демонстрации
      const mockData = {
        orders: [
          { day: 'Пн', orders: 45, revenue: 125000 },
          { day: 'Вт', orders: 52, revenue: 142000 },
          { day: 'Ср', orders: 48, revenue: 135000 },
          { day: 'Чт', orders: 60, revenue: 168000 },
          { day: 'Пт', orders: 55, revenue: 155000 },
          { day: 'Сб', orders: 40, revenue: 112000 },
          { day: 'Вс', orders: 35, revenue: 98000 },
        ],
        problems: [
          { type: 'Отсутствует товар', count: 12 },
          { type: 'Поврежден товар', count: 8 },
          { type: 'Неверный товар', count: 5 },
          { type: 'Проблема с оплатой', count: 3 },
          { type: 'Другое', count: 7 },
        ],
        couriers: [
          { name: 'Алексей К.', deliveries: 42, rating: 4.8 },
          { name: 'Дмитрий Д.', deliveries: 38, rating: 4.9 },
          { name: 'Екатерина Б.', deliveries: 35, rating: 4.7 },
          { name: 'Иван П.', deliveries: 30, rating: 4.6 },
          { name: 'Сергей М.', deliveries: 28, rating: 4.5 },
        ],
        summary: {
          totalOrders: 335,
          totalRevenue: 935000,
          avgOrderValue: 2791,
          problemRate: '4.5%',
          avgDeliveryTime: '45 мин',
          customerSatisfaction: '92%'
        }
      };
      
      // Имитация задержки загрузки
      setTimeout(() => {
        setReports(mockData);
        setLoading(false);
      }, 1000);
      
    } catch (error) {
      console.error('Ошибка при загрузке отчетов:', error);
      setLoading(false);
    }
  };

  const downloadReport = (format) => {
    alert(`Отчет будет скачан в формате ${format.toUpperCase()}`);
    // Здесь будет реальная логика скачивания
  };

  // Функция для отрисовки простого столбчатого графика (текстового)
  const renderBarChart = (data, key, color = '#0088FE', maxValue = 100) => {
    return (
      <div className="space-y-1 mt-4">
        {data.map((item, index) => {
          const percent = (item[key] / maxValue) * 100;
          return (
            <div key={index} className="flex items-center">
              <div className="w-1/4 text-sm text-gray-600">{item.day}</div>
              <div className="flex-1 ml-2">
                <div className="flex items-center">
                  <div
                    className={`h-6 rounded`}
                    style={{ 
                      width: `${percent}%`, 
                      backgroundColor: color,
                      minWidth: '20px'
                    }}
                  ></div>
                  <span className="ml-2 text-sm font-medium">{item[key]}</span>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    );
  };

  // Функция для отрисовки круговой диаграммы (текстовой)
  const renderPieChart = (data) => {
    const total = data.reduce((sum, item) => sum + item.count, 0);
    const colors = ['#0088FE', '#00C49F', '#FFBB28', '#FF8042', '#8884D8'];

    return (
      <div className="mt-4">
        {data.map((item, index) => {
          const percent = ((item.count / total) * 100).toFixed(1);
          return (
            <div key={index} className="flex items-center mb-2">
              <div 
                className="w-4 h-4 rounded-full mr-2"
                style={{ backgroundColor: colors[index % colors.length] }}
              ></div>
              <div className="flex-1">
                <div className="flex justify-between">
                  <span className="text-sm">{item.type}</span>
                  <span className="text-sm font-medium">{item.count} ({percent}%)</span>
                </div>
                <div className="w-full bg-gray-200 rounded-full h-2 mt-1">
                  <div 
                    className="h-2 rounded-full"
                    style={{ 
                      width: `${percent}%`, 
                      backgroundColor: colors[index % colors.length]
                    }}
                  ></div>
                </div>
              </div>
            </div>
          );
        })}
      </div>
    );
  };

  return (
    <div className="flex h-screen bg-gray-50">
      <OfficeSidebar />
      
      <div className="flex-1 p-8 overflow-y-auto">
        <div className="max-w-7xl mx-auto">
          {/* Заголовок */}
          <div className="flex justify-between items-center mb-8">
            <div>
              <h1 className="text-3xl font-bold text-gray-900">📊 Отчеты и аналитика</h1>
              <p className="text-gray-600">Анализ эффективности работы логистической системы</p>
            </div>
            <div className="flex gap-3">
              <select
                className="border rounded-lg px-4 py-2"
                value={dateRange}
                onChange={(e) => setDateRange(e.target.value)}
              >
                <option value="week">За неделю</option>
                <option value="month">За месяц</option>
                <option value="quarter">За квартал</option>
                <option value="year">За год</option>
              </select>
              <button
                onClick={() => downloadReport('pdf')}
                className="bg-red-500 hover:bg-red-600 text-white px-4 py-2 rounded-lg font-medium flex items-center gap-2"
              >
                <span>📄</span>
                <span>PDF отчет</span>
              </button>
              <button
                onClick={() => downloadReport('excel')}
                className="bg-green-500 hover:bg-green-600 text-white px-4 py-2 rounded-lg font-medium flex items-center gap-2"
              >
                <span>📊</span>
                <span>Excel отчет</span>
              </button>
            </div>
          </div>

          {/* Сводка */}
          {reports?.summary && (
            <div className="grid grid-cols-6 gap-4 mb-8">
              {[
                { title: 'Всего заказов', value: reports.summary.totalOrders, icon: '📦', color: 'bg-blue-500' },
                { title: 'Общая выручка', value: `${reports.summary.totalRevenue.toLocaleString()} ₽`, icon: '💰', color: 'bg-green-500' },
                { title: 'Средний чек', value: `${reports.summary.avgOrderValue} ₽`, icon: '🧾', color: 'bg-purple-500' },
                { title: 'Проблемы', value: reports.summary.problemRate, icon: '⚠️', color: 'bg-yellow-500' },
                { title: 'Ср. время доставки', value: reports.summary.avgDeliveryTime, icon: '⏱️', color: 'bg-indigo-500' },
                { title: 'Удовлетворенность', value: reports.summary.customerSatisfaction, icon: '😊', color: 'bg-pink-500' },
              ].map((stat, index) => (
                <div key={index} className="bg-white rounded-xl shadow p-5">
                  <div className="flex items-center justify-between">
                    <div>
                      <p className="text-sm text-gray-600">{stat.title}</p>
                      <p className="text-2xl font-bold mt-2">{stat.value}</p>
                    </div>
                    <div className={`${stat.color} w-12 h-12 rounded-lg flex items-center justify-center`}>
                      <span className="text-2xl">{stat.icon}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {loading ? (
            <div className="text-center py-20">
              <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500"></div>
              <p className="mt-4 text-gray-500">Загрузка отчетов...</p>
            </div>
          ) : reports && (
            <>
              {/* График заказов и выручки */}
              <div className="grid grid-cols-2 gap-8 mb-8">
                <div className="bg-white rounded-xl shadow p-6">
                  <h2 className="text-xl font-bold text-gray-900 mb-6">📈 Заказы по дням</h2>
                  {renderBarChart(reports.orders, 'orders', '#0088FE', 70)}
                </div>

                <div className="bg-white rounded-xl shadow p-6">
                  <h2 className="text-xl font-bold text-gray-900 mb-6">💰 Выручка по дням</h2>
                  {renderBarChart(reports.orders, 'revenue', '#00C49F', 200000)}
                </div>
              </div>

              {/* График проблем и рейтинг курьеров */}
              <div className="grid grid-cols-2 gap-8 mb-8">
                <div className="bg-white rounded-xl shadow p-6">
                  <h2 className="text-xl font-bold text-gray-900 mb-6">⚠️ Распределение проблем</h2>
                  {renderPieChart(reports.problems)}
                </div>

                <div className="bg-white rounded-xl shadow p-6">
                  <h2 className="text-xl font-bold text-gray-900 mb-6">🏆 Рейтинг курьеров</h2>
                  <div className="overflow-x-auto">
                    <table className="min-w-full">
                      <thead>
                        <tr className="border-b">
                          <th className="text-left py-3 px-4">Курьер</th>
                          <th className="text-left py-3 px-4">Доставки</th>
                          <th className="text-left py-3 px-4">Рейтинг</th>
                          <th className="text-left py-3 px-4">Прогресс</th>
                        </tr>
                      </thead>
                      <tbody>
                        {reports.couriers.map((courier, index) => (
                          <tr key={index} className="border-b hover:bg-gray-50">
                            <td className="py-3 px-4">
                              <div className="flex items-center">
                                <div className="w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center mr-3">
                                  <span className="text-blue-600">👤</span>
                                </div>
                                <span className="font-medium">{courier.name}</span>
                              </div>
                            </td>
                            <td className="py-3 px-4">
                              <span className="font-medium">{courier.deliveries}</span>
                            </td>
                            <td className="py-3 px-4">
                              <div className="flex items-center">
                                <span className="font-medium mr-2">{courier.rating}</span>
                                <div className="flex">
                                  {[...Array(5)].map((_, i) => (
                                    <span key={i} className={`text-lg ${i < Math.floor(courier.rating) ? 'text-yellow-500' : 'text-gray-300'}`}>
                                      ★
                                    </span>
                                  ))}
                                </div>
                              </div>
                            </td>
                            <td className="py-3 px-4">
                              <div className="w-full bg-gray-200 rounded-full h-2">
                                <div
                                  className="bg-green-500 h-2 rounded-full"
                                  style={{ width: `${(courier.deliveries / 50) * 100}%` }}
                                ></div>
                              </div>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>

              {/* Детальные отчеты */}
              <div className="bg-white rounded-xl shadow p-6 mb-8">
                <h2 className="text-xl font-bold text-gray-900 mb-6">📋 Детальные отчеты</h2>
                <div className="grid grid-cols-3 gap-4">
                  {[
                    { title: 'Отчет по клиентам', desc: 'Активность и покупки клиентов', icon: '👥' },
                    { title: 'Отчет по складу', desc: 'Остатки и движение товаров', icon: '🏪' },
                    { title: 'Отчет по возвратам', desc: 'Анализ возвратов и причин', icon: '🔄' },
                    { title: 'Финансовый отчет', desc: 'Доходы, расходы, прибыль', icon: '💳' },
                    { title: 'Отчет по эффективности', desc: 'KPI и метрики эффективности', icon: '📈' },
                    { title: 'Отчет по проблемам', desc: 'Статистика и анализ проблем', icon: '⚠️' },
                  ].map((report, index) => (
                    <button
                      key={index}
                      className="bg-gray-50 hover:bg-gray-100 border rounded-lg p-4 text-left transition-colors"
                      onClick={() => alert(`Генерация отчета: ${report.title}`)}
                    >
                      <div className="flex items-center gap-3 mb-2">
                        <span className="text-2xl">{report.icon}</span>
                        <span className="font-medium">{report.title}</span>
                      </div>
                      <p className="text-sm text-gray-600">{report.desc}</p>
                    </button>
                  ))}
                </div>
              </div>

              {/* Экспорт данных */}
              <div className="bg-blue-50 rounded-xl p-6">
                <h2 className="text-xl font-bold text-blue-900 mb-4">💾 Экспорт данных</h2>
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-blue-700">
                      Экспортируйте данные для внешнего анализа или бухгалтерского учета
                    </p>
                    <p className="text-sm text-blue-600 mt-1">
                      Доступные форматы: CSV, Excel, PDF, JSON
                    </p>
                  </div>
                  <div className="flex gap-3">
                    <button className="bg-blue-500 hover:bg-blue-600 text-white px-5 py-2.5 rounded-lg font-medium">
                      Экспорт всех данных
                    </button>
                    <button className="border border-blue-500 text-blue-500 hover:bg-blue-50 px-5 py-2.5 rounded-lg font-medium">
                      Настроить экспорт
                    </button>
                  </div>
                </div>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default OfficeReports;