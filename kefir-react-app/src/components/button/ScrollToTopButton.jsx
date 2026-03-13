import React, { useState, useEffect } from 'react';
import styles from './ScrollToTopButton.module.css';

const ScrollToTopButton = ({ scrollThreshold = 300, showBelow = 300 }) => {
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    // Функция для отслеживания прокрутки
    const toggleVisibility = () => {
      const scrolled = document.documentElement.scrollTop || document.body.scrollTop;
      
      // Показываем кнопку, если прокрутили больше порогового значения
      if (scrolled > showBelow) {
        setIsVisible(true);
      } else {
        setIsVisible(false);
      }
    };

    // Добавляем слушатель события прокрутки
    window.addEventListener('scroll', toggleVisibility);

    // Очищаем слушатель при размонтировании компонента
    return () => window.removeEventListener('scroll', toggleVisibility);
  }, [showBelow]);

  // Функция плавной прокрутки вверх
  const scrollToTop = () => {
    window.scrollTo({
      top: 0,
      behavior: 'smooth'
    });
  };

  return (
    <>
      {isVisible && (
        <button 
          className={styles.scrollTopButton}
          onClick={scrollToTop}
          aria-label="Прокрутить вверх"
          title="Наверх"
        >
          <svg 
            width="24" 
            height="24" 
            viewBox="0 0 24 24" 
            fill="none" 
            xmlns="http://www.w3.org/2000/svg"
          >
            <path 
              d="M12 4L4 12L7 12L7 20L17 20L17 12L20 12L12 4Z" 
              fill="currentColor"
            />
          </svg>
        </button>
      )}
    </>
  );
};

export default ScrollToTopButton;