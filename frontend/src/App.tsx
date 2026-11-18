import { Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import HomePage from './pages/HomePage'
import ResultsPage from './pages/ResultsPage'
import ResourcePage from './pages/ResourcePage'
import SourcesPage from './pages/SourcesPage'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        <Route path="search" element={<ResultsPage />} />
        <Route path="resources/:id" element={<ResourcePage />} />
        <Route path="sources" element={<SourcesPage />} />
        <Route path="*" element={<p className="py-16 text-center">Page not found.</p>} />
      </Route>
    </Routes>
  )
}
